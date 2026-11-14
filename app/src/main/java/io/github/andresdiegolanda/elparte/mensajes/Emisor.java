package io.github.andresdiegolanda.elparte.mensajes;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica mensajes en su tópico, con la corrida como clave (docs/contratos.md). El envío espera la
 * confirmación de Kafka, así un agente no marca un mensaje como procesado si lo que produjo no salió.
 */
@Component
public class Emisor {

    private static final int SEGUNDOS_DE_ESPERA = 30;

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final Clock reloj;

    public Emisor(KafkaTemplate<String, String> kafka, JsonMapper json, Clock reloj) {
        this.kafka = kafka;
        this.json = json;
        this.reloj = reloj;
    }

    /**
     * @param posicion lugar de este mensaje entre los que produce la misma causa; junto con la causa,
     *                 hace que el identificador sea determinista
     */
    public Sobre emitir(String corridaId, String tipo, String agente, String causaId, int posicion, Object contenido) {
        Sobre sobre = new Sobre(
                Huellas.mensajeId(corridaId, agente, tipo, causaId, posicion),
                tipo,
                Tipos.VERSION,
                corridaId,
                causaId,
                agente,
                reloj.instant().truncatedTo(ChronoUnit.MILLIS),
                json.valueToTree(contenido));
        try {
            kafka.send(tipo, corridaId, json.writeValueAsString(sobre)).get(SEGUNDOS_DE_ESPERA, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el envío de " + sobre.mensajeId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar " + sobre.mensajeId() + " a " + tipo, e);
        }
        return sobre;
    }
}
