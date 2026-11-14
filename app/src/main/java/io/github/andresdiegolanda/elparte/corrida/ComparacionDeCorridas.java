package io.github.andresdiegolanda.elparte.corrida;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Compara los mensajes de una corrida con los de su reproducción (docs/plan.md: "la corrida repetida
 * produce mensajes idénticos a los de la original").
 *
 * <p>Se compara el tipo, el agente y el contenido de cada mensaje. Quedan afuera lo que por definición
 * cambia: los identificadores (llevan la corrida), las horas, {@code corrida.iniciada} (dice el modo),
 * {@code uso.modelo} (la reproducción no llama al modelo) y el costo y la duración de
 * {@code corrida.terminada}.
 */
@Component
public class ComparacionDeCorridas {

    private static final Set<String> FUERA = Set.of(Tipos.CORRIDA_INICIADA, Tipos.USO_MODELO);

    private final RegistroDeMensajes registro;
    private final JsonMapper json;

    public ComparacionDeCorridas(RegistroDeMensajes registro, JsonMapper json) {
        this.registro = registro;
        this.json = json;
    }

    public record Resultado(boolean iguales, int mensajesOriginal, int mensajesRepeticion, List<String> soloEnOriginal,
            List<String> soloEnRepeticion) {
    }

    public Resultado comparar(String original, String repeticion) {
        List<String> a = normalizar(original);
        List<String> b = normalizar(repeticion);
        List<String> soloA = new ArrayList<>(a);
        b.forEach(soloA::remove);
        List<String> soloB = new ArrayList<>(b);
        a.forEach(soloB::remove);
        return new Resultado(soloA.isEmpty() && soloB.isEmpty(), a.size(), b.size(), soloA, soloB);
    }

    private List<String> normalizar(String corridaId) {
        List<String> mensajes = new ArrayList<>();
        for (Sobre sobre : registro.deCorrida(corridaId)) {
            if (FUERA.contains(sobre.tipo())) {
                continue;
            }
            JsonNode contenido = sobre.contenido().deepCopy();
            if (Tipos.CORRIDA_TERMINADA.equals(sobre.tipo()) && contenido instanceof ObjectNode objeto) {
                objeto.remove("costoUsd");
                objeto.remove("duracionSegundos");
            }
            if (Tipos.CORRIDA_CORTADA.equals(sobre.tipo()) && contenido instanceof ObjectNode objeto) {
                objeto.remove("gastadoUsd");
                objeto.remove("minutos");
                objeto.remove("pasos");
            }
            mensajes.add(sobre.tipo() + " | " + sobre.agente() + " | " + json.writeValueAsString(contenido));
        }
        Collections.sort(mensajes);
        return mensajes;
    }
}
