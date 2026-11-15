package io.github.andresdiegolanda.elparte.fuentes.arce;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.FallaDeArce;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.ItemDelFeed;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.Leida;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ExploracionTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.FuenteFallida;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El explorador de ARCE (ADR 0006): lee lo que se publicó la noche objetivo, se queda con las
 * compras de las entidades que alguien sigue y emite un candidato por cada una.
 *
 * <ol>
 * <li>Lee el feed mensual y se queda con lo publicado la fecha objetivo y los días de margen
 * anteriores, para levantar lo que ARCE publica con atraso. El archivista descarta lo ya conocido.</li>
 * <li>Lee cada publicación para saber quién compra. El feed no lo dice, así que hay que leerlas a
 * todas; el filtro por comprador se hace antes de cualquier llamada al modelo.</li>
 * <li>Tapa los datos personales de las que quedan (ADR 0008) y arma los candidatos.</li>
 * </ol>
 *
 * <p>Si hay más candidatos que el máximo de la corrida, entran primero los de la fecha objetivo: lo que
 * queda afuera es del día de margen.
 *
 * <p>Todo lo que lee se graba ya enmascarado, para poder repetir la corrida sin red. En modo banco, lo
 * que la corrida de la noche anterior ya leyó de una publicación se toma de su grabación en lugar de
 * pedirlo otra vez, y se graba igual en esta corrida (ADR 0014).
 */
@Component
public class ExploradorArce {

    public static final String AGENTE = "explorador-arce";
    private static final Logger log = LoggerFactory.getLogger(ExploradorArce.class);
    private static final int POSICION_DE_FALLAS = 100_000;

    private final LectorArce lector;
    private final Grabaciones grabaciones;
    private final Corridas corridas;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final int diasDeMargen;
    private final int concurrencia;
    private final int maximoCandidatos;

    public ExploradorArce(LectorArce lector, Grabaciones grabaciones, Corridas corridas, Emisor emisor,
            Procesados procesados, JdbcClient jdbc, JsonMapper json,
            @Value("${elparte.arce.dias-de-margen:1}") int diasDeMargen,
            @Value("${elparte.arce.concurrencia:4}") int concurrencia,
            @Value("${elparte.corrida.maximo-candidatos:60}") int maximoCandidatos) {
        this.lector = lector;
        this.grabaciones = grabaciones;
        this.corridas = corridas;
        this.emisor = emisor;
        this.procesados = procesados;
        this.jdbc = jdbc;
        this.json = json;
        this.diasDeMargen = diasDeMargen;
        this.concurrencia = concurrencia;
        this.maximoCandidatos = maximoCandidatos;
    }

    @KafkaListener(topics = Tipos.CORRIDA_INICIADA, groupId = AGENTE)
    public void recibir(String valor) {
        Sobre inicio = json.readValue(valor, Sobre.class);
        CorridaIniciada corrida = json.treeToValue(inicio.contenido(), CorridaIniciada.class);
        if (!corrida.fuentes().contains("arce") || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        explorar(inicio, corrida);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void explorar(Sobre inicio, CorridaIniciada corrida) {
        String corridaId = inicio.corridaId();
        Map<String, String> seguidos = compradoresSeguidos(corrida.entidadesSeguidas());
        String anterior = "banco".equals(corrida.modo()) ? corridas.anteriorDelBanco(corridaId).orElse(null) : null;
        LocalDate fecha = LocalDate.parse(corrida.fechaObjetivo());

        List<ItemDelFeed> items;
        try {
            items = itemsDeLaVentana(corridaId, fecha);
        } catch (FallaDeArce falla) {
            fallo(inicio, 0, falla);
            emisor.emitir(corridaId, Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                    new ExploracionTerminada("arce", 0, 0, 0));
            return;
        }

        List<Leido> leidos = new ArrayList<>();
        List<FallaDeArce> fallas = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(concurrencia)) {
            List<Future<Optional<Candidato>>> pendientes = new ArrayList<>();
            for (ItemDelFeed item : items) {
                pendientes.add(hilos.submit(() -> consultar(corridaId, anterior, item, seguidos)));
            }
            for (int i = 0; i < pendientes.size(); i++) {
                try {
                    leidos.add(new Leido(items.get(i), pendientes.get(i).get()));
                } catch (Exception e) {
                    if (e.getCause() instanceof FallaDeArce falla) {
                        fallas.add(falla);
                    } else {
                        throw new IllegalStateException("Falló la exploración de ARCE", e);
                    }
                }
            }
        }

        int emitidos = 0;
        int sinProcesar = fallas.size();
        for (Candidato sinId : paraElTope(leidos, fecha)) {
            if (emitidos == maximoCandidatos) {
                sinProcesar++;
                continue;
            }
            emitidos++;
            Candidato candidato = new Candidato(String.format("k-arce-%04d", emitidos), sinId.fuente(),
                    sinId.identificadorEnFuente(), sinId.proceso(), sinId.url(), sinId.fechaPublicacion(),
                    sinId.tipoDeHecho(), sinId.entidades(), sinId.resumen(), sinId.evidencia(),
                    sinId.datosPersonalesEnmascarados());
            emisor.emitir(corridaId, Tipos.CANDIDATOS, AGENTE, inicio.mensajeId(), emitidos, candidato);
        }
        for (int i = 0; i < fallas.size(); i++) {
            fallo(inicio, i + 1, fallas.get(i));
        }
        log.info("Exploración de ARCE de {}: {} publicaciones leídas, {} candidatos, {} sin procesar",
                corridaId, items.size(), emitidos, sinProcesar);
        emisor.emitir(corridaId, Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                new ExploracionTerminada("arce", items.size(), emitidos, sinProcesar));
    }

    /** Una publicación de la ventana y lo que dio leerla: un candidato si es de una entidad seguida. */
    record Leido(ItemDelFeed item, Optional<Candidato> candidato) {
    }

    /**
     * Los candidatos en el orden en que entran al tope de la corrida: primero los de la fecha objetivo y
     * después los de los días de margen, cada grupo en orden de publicación. Así, si hay más que el
     * máximo, lo que queda afuera es del margen, que la noche anterior ya vio.
     */
    static List<Candidato> paraElTope(List<Leido> leidos, LocalDate fecha) {
        return leidos.stream()
                .filter(leido -> leido.candidato().isPresent())
                .sorted(Comparator.comparing((Leido leido) -> !leido.item().publicada().toLocalDate().equals(fecha)))
                .map(leido -> leido.candidato().get())
                .toList();
    }

    /** Lo publicado la fecha objetivo y los días de margen anteriores, en orden. */
    private List<ItemDelFeed> itemsDeLaVentana(String corridaId, LocalDate fecha) {
        LocalDate desde = fecha.minusDays(diasDeMargen);
        JsonNode grabado = grabaciones.conGrabacion(corridaId, "arce:feed:" + desde + ":" + fecha, "fuente", () -> {
            Set<YearMonth> meses = new LinkedHashSet<>(List.of(YearMonth.from(desde), YearMonth.from(fecha)));
            ArrayNode lista = json.createArrayNode();
            List<ItemDelFeed> delFeed = new ArrayList<>();
            try {
                meses.forEach(mes -> delFeed.addAll(lector.feedDelMes(mes)));
            } catch (FallaDeArce falla) {
                return comoGrabacion(falla);
            }
            delFeed.stream()
                    .filter(item -> !item.publicada().toLocalDate().isBefore(desde)
                            && !item.publicada().toLocalDate().isAfter(fecha))
                    .sorted(Comparator.comparing(ItemDelFeed::publicada).thenComparing(ItemDelFeed::publicacionId))
                    .forEach(item -> lista.add(json.createObjectNode()
                            .put("id", item.publicacionId())
                            .put("publicada", item.publicada().toString())
                            .put("categoria", item.categoria())));
            return lista;
        });
        lanzarSiFallo(grabado);
        List<ItemDelFeed> items = new ArrayList<>();
        for (JsonNode item : grabado) {
            items.add(new ItemDelFeed(item.path("id").asString(),
                    java.time.LocalDateTime.parse(item.path("publicada").asString()), item.path("categoria").asString()));
        }
        return items;
    }

    /**
     * Lee una publicación una sola vez: graba quién compra, y si es una entidad seguida, graba también
     * la publicación ya enmascarada y arma el candidato (sin identificador todavía).
     */
    private Optional<Candidato> consultar(String corridaId, String anterior, ItemDelFeed item,
            Map<String, String> seguidos) {
        String claveComprador = "arce:comprador:" + item.publicacionId();
        String clavePublicacion = "arce:publicacion:" + item.publicacionId();
        Map<String, JsonNode> leida = new HashMap<>();
        JsonNode comprador = grabaciones.conGrabacion(corridaId, claveComprador, "fuente", deLaAnterior(anterior, claveComprador, () -> {
            Leida publicacion;
            try {
                publicacion = lector.leer(item.publicacionId());
            } catch (FallaDeArce falla) {
                return comoGrabacion(falla);
            }
            String id = publicacion.publicacion().path("buyer").path("id").asString("");
            if (seguidos.containsKey(id)) {
                Enmascarador.Enmascarada enmascarada = Enmascarador.enmascarar(publicacion.publicacion());
                ObjectNode guardada = json.createObjectNode();
                guardada.set("publicacion", enmascarada.publicacion());
                guardada.put("tapados", enmascarada.tapados());
                guardada.put("hashContenido", publicacion.hashContenido());
                leida.put("publicacion", guardada);
            }
            return json.createObjectNode().put("comprador", id);
        }));
        lanzarSiFallo(comprador);
        String entidad = seguidos.get(comprador.path("comprador").asString(""));
        if (entidad == null) {
            return Optional.empty();
        }
        JsonNode guardada = grabaciones.conGrabacion(corridaId, clavePublicacion, "fuente", deLaAnterior(anterior, clavePublicacion, () -> {
            if (leida.containsKey("publicacion")) {
                return leida.get("publicacion");
            }
            Leida publicacion;
            try {
                publicacion = lector.leer(item.publicacionId());
            } catch (FallaDeArce falla) {
                return comoGrabacion(falla);
            }
            Enmascarador.Enmascarada enmascarada = Enmascarador.enmascarar(publicacion.publicacion());
            ObjectNode nueva = json.createObjectNode();
            nueva.set("publicacion", enmascarada.publicacion());
            nueva.put("tapados", enmascarada.tapados());
            nueva.put("hashContenido", publicacion.hashContenido());
            return nueva;
        }));
        lanzarSiFallo(guardada);
        return Optional.of(CandidatosDeArce.armar(null, guardada.path("publicacion"), entidad,
                guardada.path("tapados").asInt(), guardada.path("hashContenido").asString()));
    }

    /**
     * La lectura de una publicación: si hay corrida anterior del banco y ella la leyó bien, su grabación;
     * si no, la lectura de verdad. Una falla grabada no se reusa: se vuelve a intentar.
     */
    private Supplier<JsonNode> deLaAnterior(String anterior, String clave, Supplier<JsonNode> lectura) {
        if (anterior == null) {
            return lectura;
        }
        return () -> grabaciones.leer(anterior, clave).filter(grabado -> !grabado.has("falla")).orElseGet(lectura);
    }

    /** Código de comprador en ARCE de cada entidad seguida, sacado de {@code parte.entidades}. */
    private Map<String, String> compradoresSeguidos(List<String> entidades) {
        Map<String, String> seguidos = new HashMap<>();
        for (String entidad : entidades) {
            jdbc.sql("select identificadores from parte.entidades where id = :id").param("id", entidad)
                    .query(String.class).optional()
                    .map(json::readTree)
                    .ifPresent(ids -> ids.path("arce").forEach(codigo -> seguidos.put(codigo.asString(), entidad)));
        }
        return seguidos;
    }

    /**
     * Una falla de la fuente también se graba: así la corrida repetida falla en el mismo lugar y emite el
     * mismo {@code fuente.fallida}, sin salir a la red.
     */
    private JsonNode comoGrabacion(FallaDeArce falla) {
        return json.createObjectNode()
                .put("falla", falla.tipoDeFalla())
                .put("url", falla.url())
                .put("detalle", falla.getMessage());
    }

    private static void lanzarSiFallo(JsonNode grabado) {
        if (grabado.has("falla")) {
            throw new FallaDeArce(grabado.path("falla").asString(), grabado.path("url").asString(),
                    grabado.path("detalle").asString());
        }
    }

    private void fallo(Sobre inicio, int numero, FallaDeArce falla) {
        emisor.emitir(inicio.corridaId(), Tipos.FUENTE_FALLIDA, AGENTE, inicio.mensajeId(), POSICION_DE_FALLAS + numero,
                new FuenteFallida("arce", falla.url(), falla.tipoDeFalla(), falla.getMessage()));
    }
}
