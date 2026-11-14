package io.github.andresdiegolanda.elparte.corrida;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.infraestructura.Huellas;

/**
 * El contexto de cada corrida: lo que dijo su {@code corrida.iniciada}. Los agentes lo consultan para
 * saber la fecha objetivo, las entidades seguidas y el modelo que les toca (ADR 0003).
 */
@Component
public class Corridas {

    private final RegistroDeMensajes registro;
    private final JdbcClient jdbc;
    private final Map<String, CorridaIniciada> cache = new ConcurrentHashMap<>();

    public Corridas(RegistroDeMensajes registro, JdbcClient jdbc) {
        this.registro = registro;
        this.jdbc = jdbc;
    }

    public CorridaIniciada contexto(String corridaId) {
        return cache.computeIfAbsent(corridaId, id -> registro.contenido(
                registro.esperar(Huellas.mensajeId(id, "inicio", Tipos.CORRIDA_INICIADA, null, 0)),
                CorridaIniciada.class));
    }

    /** El identificador del mensaje {@code corrida.iniciada} de una corrida. */
    public static String idDeInicio(String corridaId) {
        return Huellas.mensajeId(corridaId, "inicio", Tipos.CORRIDA_INICIADA, null, 0);
    }

    public boolean esReproduccion(String corridaId) {
        return "reproduccion".equals(contexto(corridaId).modo());
    }

    public String estado(String corridaId) {
        return jdbc.sql("select estado from parte.corridas where id = :id").param("id", corridaId)
                .query(String.class).optional().orElse("desconocida");
    }
}
