package io.github.andresdiegolanda.elparte.observador;

import java.sql.Timestamp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El observador: lee todos los tópicos y copia cada mensaje a {@code parte.mensajes} (ADR 0002).
 * El registro de mensajes es la historia de la corrida, y la sala de máquinas sale de acá.
 *
 * <p>Si un mensaje llega dos veces, la segunda escritura no hace nada: la clave es el identificador
 * determinista del mensaje.
 */
@Component
public class Observador {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public Observador(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(groupId = "observador", topics = {
            Tipos.CORRIDA_INICIADA, Tipos.CANDIDATOS, Tipos.EXPLORACION_TERMINADA, Tipos.CANDIDATOS_COTEJADOS,
            Tipos.CANDIDATOS_GRADUADOS, Tipos.HECHOS_ANALIZADOS, Tipos.INFORMES, Tipos.REVISION_PENDIENTE,
            Tipos.FUENTE_FALLIDA, Tipos.USO_MODELO, Tipos.CORRIDA_CORTADA, Tipos.CORRIDA_TERMINADA,
            Tipos.OBSERVACIONES_PROPUESTAS, Tipos.OBSERVACIONES_GRADUADAS })
    public void recibir(String valor) {
        Sobre sobre = json.readValue(valor, Sobre.class);
        jdbc.sql("""
                insert into parte.mensajes (mensaje_id, tipo, version, corrida_id, causa_id, agente, emitido_en, contenido)
                values (:id, :tipo, :version, :corrida, :causa, :agente, :emitido, cast(:contenido as jsonb))
                on conflict (mensaje_id) do nothing
                """)
                .param("id", sobre.mensajeId())
                .param("tipo", sobre.tipo())
                .param("version", sobre.version())
                .param("corrida", sobre.corridaId())
                .param("causa", sobre.causaId())
                .param("agente", sobre.agente())
                .param("emitido", Timestamp.from(sobre.emitidoEn()))
                .param("contenido", json.writeValueAsString(sobre.contenido()))
                .update();
    }
}
