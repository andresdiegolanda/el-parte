package io.github.andresdiegolanda.elparte.corrida;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Presupuesto;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El inicio de una corrida: anota la corrida en la base y emite {@code corrida.iniciada}, que arranca
 * la noche (docs/contratos.md).
 *
 * <p>Una corrida normal lee las fuentes activas para las entidades que alguien sigue, con el modelo
 * vigente de cada agente. Una del banco hace lo mismo para una noche del registro del banco de
 * pruebas, siguiendo solo la entidad que se mide (ADR 0014). Una de reproducción copia todo eso de la
 * corrida que repite.
 *
 * <p>La corrida se anota en la base antes de emitir el mensaje, y sin transacción abierta, para que el
 * observador encuentre la corrida cuando guarde el mensaje.
 */
@Service
public class InicioDeCorrida {

    public static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    private final JdbcClient jdbc;
    private final Emisor emisor;
    private final RegistroDeMensajes registro;
    private final JsonMapper json;
    private final Clock reloj;
    private final Presupuesto presupuesto;

    public InicioDeCorrida(JdbcClient jdbc, Emisor emisor, RegistroDeMensajes registro, JsonMapper json, Clock reloj,
            @Value("${elparte.corrida.costo-maximo-usd:1.25}") BigDecimal costoMaximo,
            @Value("${elparte.corrida.pasos-maximos:200}") int pasosMaximos,
            @Value("${elparte.corrida.minutos-maximos:45}") int minutosMaximos) {
        this.jdbc = jdbc;
        this.emisor = emisor;
        this.registro = registro;
        this.json = json;
        this.reloj = reloj;
        this.presupuesto = new Presupuesto(costoMaximo, pasosMaximos, minutosMaximos);
    }

    /** La fecha objetivo por omisión: ayer, en Uruguay. */
    public LocalDate ayer() {
        return LocalDate.now(reloj.withZone(URUGUAY)).minusDays(1);
    }

    public String iniciarNormal(LocalDate fechaObjetivo) {
        return iniciarNormal(fechaObjetivo, presupuesto);
    }

    /** Una noche normal con otro presupuesto que el de la configuración. */
    public String iniciarNormal(LocalDate fechaObjetivo, Presupuesto presupuesto) {
        List<String> entidades = jdbc.sql("select distinct entidad_id from parte.suscripciones order by entidad_id")
                .query(String.class).list();
        CorridaIniciada inicio = new CorridaIniciada("normal", fechaObjetivo.toString(), fuentesActivas(), entidades,
                presupuesto, modelosVigentes(), null);
        return iniciar(inicio);
    }

    /** Una noche del banco de pruebas: la fecha del registro, y solo la entidad que se mide (ADR 0014). */
    public String iniciarBanco(LocalDate fechaObjetivo, String entidad) {
        CorridaIniciada inicio = new CorridaIniciada("banco", fechaObjetivo.toString(), fuentesActivas(),
                List.of(entidad), presupuesto, modelosVigentes(), null);
        return iniciar(inicio);
    }

    /** Repite una corrida normal o del banco con lo que ella grabó: sin red ni costo. */
    public String iniciarReproduccion(String corridaOriginal) {
        CorridaIniciada original = registro.contenido(registro.esperar(Corridas.idDeInicio(corridaOriginal)),
                CorridaIniciada.class);
        if (!"normal".equals(original.modo()) && !"banco".equals(original.modo())) {
            throw new IllegalArgumentException("Solo se repite una corrida normal o del banco; " + corridaOriginal
                    + " es " + original.modo());
        }
        CorridaIniciada inicio = new CorridaIniciada("reproduccion", original.fechaObjetivo(), original.fuentes(),
                original.entidadesSeguidas(), original.presupuesto(), original.modelos(), corridaOriginal);
        return iniciar(inicio);
    }

    private List<String> fuentesActivas() {
        return jdbc.sql("select id from parte.fuentes where activa order by id").query(String.class).list();
    }

    private Map<String, String> modelosVigentes() {
        Map<String, String> modelos = new LinkedHashMap<>();
        jdbc.sql("select distinct on (agente) agente, modelo from parte.modelos_de_agente order by agente, desde desc")
                .query((fila, n) -> Map.entry(fila.getString("agente"), fila.getString("modelo")))
                .list()
                .forEach(par -> modelos.put(par.getKey(), par.getValue()));
        return modelos;
    }

    private String iniciar(CorridaIniciada inicio) {
        String corridaId = nuevoId(inicio.fechaObjetivo(), inicio.modo());
        jdbc.sql("""
                insert into parte.corridas (id, modo, fecha_objetivo, estado, presupuesto_usd, presupuesto_pasos,
                                            presupuesto_minutos, modelos, iniciada_en)
                values (:id, :modo, :fecha, 'en_curso', :usd, :pasos, :minutos, cast(:modelos as jsonb), :inicio)
                """)
                .param("id", corridaId)
                .param("modo", inicio.modo())
                .param("fecha", LocalDate.parse(inicio.fechaObjetivo()))
                .param("usd", inicio.presupuesto().costoMaximoUsd())
                .param("pasos", inicio.presupuesto().pasosMaximos())
                .param("minutos", inicio.presupuesto().minutosMaximos())
                .param("modelos", json.writeValueAsString(inicio.modelos()))
                .param("inicio", Timestamp.from(reloj.instant()))
                .update();
        jdbc.sql("insert into parte.libros_de_corrida (corrida_id) values (:id)").param("id", corridaId).update();
        emisor.emitir(corridaId, Tipos.CORRIDA_INICIADA, "inicio", null, 0, inicio);
        return corridaId;
    }

    /** {@code c-<fecha>-<modo>}, y si ya existe, con un número al final. */
    private String nuevoId(String fecha, String modo) {
        String base = "c-" + fecha + "-" + modo;
        int existentes = jdbc.sql("select count(*) from parte.corridas where id = :base or id like :patron")
                .param("base", base)
                .param("patron", base + "-%")
                .query(Integer.class).single();
        return existentes == 0 ? base : base + "-" + (existentes + 1);
    }
}
