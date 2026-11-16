package io.github.andresdiegolanda.elparte.banco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.fuentes.arce.ExploradorArce;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * La salida de El Parte de una medición contra el banco, con la forma de banco-de-pruebas/formato.md
 * ("La salida de El Parte"), armada desde las corridas del banco de esta base y sus mensajes.
 *
 * <ul>
 * <li>Cada corrida: su estado; el costo y la duración de {@code corrida.terminada}; las fuentes que no
 * dejaron pedir la fecha o que vinieron vacías un día hábil ({@code fuente.fallida} de tipo
 * {@code sinFecha} o {@code sinPublicaciones}), que no se pueden repetir esa noche, y las demás
 * lecturas fallidas.</li>
 * <li>Cada clave de hecho de {@code candidatos.graduados}: la primera noche en que salió PROVEN o
 * INFERRED, con ese grado; si nunca salió así, la primera en que salió NOT_MENTIONED.</li>
 * </ul>
 *
 * <p>No lleva nada del registro del banco: solo claves de hechos de fuentes públicas, fechas y conteos.
 */
@Component
public class SalidaDelBanco {

    public static final String ARCHIVO = "salida-de-el-parte.json";
    static final String SIN_FECHA = "sinFecha";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public SalidaDelBanco(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private record Corrida(String id, LocalDate noche, String estado, Timestamp iniciada, Timestamp terminada) {
    }

    private record Graduado(String clave, LocalDate noche, String grado) {
    }

    public void escribir(Path archivo, String entidad, String commit) {
        try {
            Files.createDirectories(archivo.getParent());
            Files.writeString(archivo, json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build()
                    .writeValueAsString(armar(entidad, commit)) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir la salida en " + archivo, e);
        }
    }

    public Map<String, Object> armar(String entidad, String commit) {
        List<Corrida> corridas = new ArrayList<>();
        TreeSet<LocalDate> noches = new TreeSet<>();
        jdbc.sql("""
                select id, fecha_objetivo, estado, iniciada_en, terminada_en from parte.corridas
                where modo = 'banco' and estado <> 'en_curso'
                order by fecha_objetivo, iniciada_en
                """)
                .query((f, n) -> new Corrida(f.getString(1), f.getObject(2, LocalDate.class), f.getString(3),
                        f.getTimestamp(4), f.getTimestamp(5)))
                .list()
                .forEach(c -> {
                    // Una sola corrida por noche: si hubiera dos, cuenta la primera.
                    if (noches.add(c.noche())) {
                        corridas.add(c);
                    }
                });

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("commit", commit == null ? "" : commit);
        salida.put("entidad", entidad);
        JsonNode inicio = corridas.isEmpty() ? null : mensaje(corridas.getFirst().id(), "corrida.iniciada");
        salida.put("fuentes", inicio == null ? List.of() : inicio.path("fuentes"));
        salida.put("modelos", inicio == null ? Map.of() : inicio.path("modelos"));
        salida.put("corridas", corridas.stream().map(this::deLaCorrida).toList());
        salida.put("hechos", hechos(corridas));
        return salida;
    }

    private Map<String, Object> deLaCorrida(Corrida c) {
        JsonNode terminada = mensaje(c.id(), "corrida.terminada");
        TreeSet<String> noRepetibles = new TreeSet<>();
        int fallidas = 0;
        for (JsonNode falla : mensajes(c.id(), "fuente.fallida")) {
            String tipo = falla.path("tipoDeFalla").asString();
            if (SIN_FECHA.equals(tipo) || ExploradorArce.SIN_PUBLICACIONES.equals(tipo)) {
                noRepetibles.add(falla.path("fuente").asString());
            } else {
                fallidas++;
            }
        }
        BigDecimal costo = terminada != null && terminada.has("costoUsd")
                ? terminada.path("costoUsd").decimalValue()
                : jdbc.sql("""
                        select coalesce(sum((contenido->>'costoUsd')::numeric), 0) from parte.mensajes
                        where corrida_id = :c and tipo = 'uso.modelo'
                        """).param("c", c.id()).query(BigDecimal.class).single();
        long duracion = terminada != null && terminada.has("duracionSegundos")
                ? terminada.path("duracionSegundos").asLong()
                : c.terminada() == null ? 0 : Duration.between(c.iniciada().toInstant(), c.terminada().toInstant()).toSeconds();

        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("noche", c.noche().toString());
        fila.put("corrida", c.id());
        fila.put("estado", c.estado());
        fila.put("noRepetibles", List.copyOf(noRepetibles));
        fila.put("costoUsd", simple(costo));
        fila.put("duracionSegundos", duracion);
        fila.put("lecturasFallidas", fallidas);
        return fila;
    }

    private List<Map<String, Object>> hechos(List<Corrida> corridas) {
        Map<String, LocalDate> nochePorCorrida = new LinkedHashMap<>();
        corridas.forEach(c -> nochePorCorrida.put(c.id(), c.noche()));
        List<Graduado> graduados = new ArrayList<>();
        jdbc.sql("""
                select m.corrida_id, m.contenido->>'claveDeHecho', m.contenido->>'grado'
                from parte.mensajes m join parte.corridas c on c.id = m.corrida_id
                where c.modo = 'banco' and m.tipo = 'candidatos.graduados'
                """)
                .query((f, n) -> new String[] { f.getString(1), f.getString(2), f.getString(3) })
                .list()
                .forEach(g -> {
                    LocalDate noche = nochePorCorrida.get(g[0]);
                    if (noche != null && g[1] != null) {
                        graduados.add(new Graduado(g[1], noche, g[2]));
                    }
                });
        graduados.sort((a, b) -> a.noche().equals(b.noche()) ? a.clave().compareTo(b.clave()) : a.noche().compareTo(b.noche()));

        Map<String, Graduado> elegidos = new LinkedHashMap<>();
        for (Graduado g : graduados) {
            Graduado actual = elegidos.get(g.clave());
            boolean aLaVista = !"NOT_MENTIONED".equals(g.grado());
            if (actual == null || (aLaVista && "NOT_MENTIONED".equals(actual.grado()))) {
                elegidos.put(g.clave(), g);
            }
        }
        return elegidos.values().stream()
                .sorted((a, b) -> a.noche().equals(b.noche()) ? a.clave().compareTo(b.clave()) : a.noche().compareTo(b.noche()))
                .map(g -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("clave", g.clave());
                    fila.put("noche", g.noche().toString());
                    fila.put("grado", g.grado());
                    return fila;
                })
                .toList();
    }

    /** Sin ceros de más y sin notación científica: 0.0080 es 0.008, y 10 sigue siendo 10. */
    private static BigDecimal simple(BigDecimal valor) {
        BigDecimal sinCeros = valor.stripTrailingZeros();
        return sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros;
    }

    private JsonNode mensaje(String corridaId, String tipo) {
        List<JsonNode> todos = mensajes(corridaId, tipo);
        return todos.isEmpty() ? null : todos.getFirst();
    }

    private List<JsonNode> mensajes(String corridaId, String tipo) {
        return jdbc.sql("select contenido from parte.mensajes where corrida_id = :c and tipo = :t order by emitido_en, mensaje_id")
                .param("c", corridaId)
                .param("t", tipo)
                .query(String.class).list().stream()
                .map(json::readTree)
                .toList();
    }
}
