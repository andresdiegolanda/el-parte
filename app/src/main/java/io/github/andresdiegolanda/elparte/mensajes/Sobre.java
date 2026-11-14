package io.github.andresdiegolanda.elparte.mensajes;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * El sobre común de todos los mensajes (docs/contratos.md, "El sobre").
 *
 * @param mensajeId identificador determinista, calculado a partir de sus causas
 * @param tipo      el tipo de mensaje, igual al nombre de su tópico
 * @param version   versión del contrato de ese tipo
 * @param corridaId la corrida a la que pertenece
 * @param causaId   el mensaje que lo provocó; nulo solo en {@code corrida.iniciada}
 * @param agente    quién lo emitió
 * @param emitidoEn cuándo se emitió
 * @param contenido lo propio de cada tipo
 */
public record Sobre(
        String mensajeId,
        String tipo,
        int version,
        String corridaId,
        String causaId,
        String agente,
        Instant emitidoEn,
        JsonNode contenido) {
}
