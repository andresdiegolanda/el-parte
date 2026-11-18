package io.github.andresdiegolanda.elparte.lectura;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.andresdiegolanda.elparte.analista.Analista;

/**
 * El control en código de una observación del lector de expedientes, antes del refutador (ADR 0018). Sin
 * el modelo: es gratis, igual en cada repetición y fácil de probar.
 *
 * <ul>
 * <li>Su clase es una de las cuatro.</li>
 * <li>Cita dos hechos distintos o más, y todos estaban en la entrada.</li>
 * <li>Cada cifra está en las afirmaciones o las noches de los hechos que cita, con la comparación del
 * analista (ADR 0015).</li>
 * <li>No usa palabras de juicio: dice qué se repite, no qué significa.</li>
 * </ul>
 */
public final class ControlDeObservaciones {

    public static final Set<String> CLASES = Set.of("repeticion", "cadena", "proveedor", "otra");

    /** Raíces de palabras que califican, recomiendan o explican causas, sin tildes y en minúsculas. */
    static final List<String> DE_JUICIO = List.of("irregular", "sospech", "favorec", "corrup", "fraud", "ilegal",
            "ilicit", "direccionad", "arbitrari", "abus", "desvi", "deberia", "recomend", "convendria", "conviene",
            "preocupa", "alarm", "grave", "escandal", "opac", "dudos", "anomal", "porque", "debido a", "a causa de");

    private ControlDeObservaciones() {
    }

    /** Un hecho que el lector leyó: su clave, su noche, su grado y su afirmación. */
    public record HechoLeido(String clave, String noche, String grado, String afirmacion) {
    }

    /**
     * Nulo si la observación pasa el control; si no, por qué no. {@code leidos} son los hechos de la
     * entrada, por clave.
     */
    public static String descarte(String clase, String texto, List<String> hechos, Map<String, HechoLeido> leidos) {
        if (clase == null || !CLASES.contains(clase.trim())) {
            return "su clase no es ninguna de las cuatro (" + clase + ").";
        }
        if (texto == null || texto.isBlank()) {
            return "no dice nada.";
        }
        Set<String> citados = hechos == null ? Set.of()
                : hechos.stream().filter(h -> h != null && !h.isBlank()).map(String::trim)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (citados.size() < 2) {
            return "cita menos de dos hechos.";
        }
        List<String> ajenos = citados.stream().filter(h -> !leidos.containsKey(h)).toList();
        if (!ajenos.isEmpty()) {
            return "cita hechos que no estaban en la entrada: " + String.join(", ", ajenos) + ".";
        }
        StringBuilder fuente = new StringBuilder();
        for (String clave : citados) {
            HechoLeido hecho = leidos.get(clave);
            fuente.append(hecho.afirmacion()).append('\n').append(hecho.noche()).append('\n');
        }
        Set<String> inventadas = new HashSet<>(Analista.cifras(texto));
        inventadas.removeAll(Analista.cifrasPermitidas(fuente.toString()));
        // La cantidad de hechos citados se puede decir: "tres adjudicaciones" sale de contar las citas.
        inventadas.remove(String.valueOf(citados.size()));
        if (!inventadas.isEmpty()) {
            return "tiene cifras que no están en los hechos que cita: " + String.join(", ", inventadas.stream().sorted().toList()) + ".";
        }
        String normalizado = sinTildes(texto.toLowerCase());
        for (String palabra : DE_JUICIO) {
            if (Pattern.compile("\\b" + Pattern.quote(palabra)).matcher(normalizado).find()) {
                return "califica, recomienda o explica una causa (\"" + palabra + "\").";
            }
        }
        return null;
    }

    /** El grado más bajo de los hechos citados: una observación no puede valer más que ellos. */
    public static String gradoMaximo(List<String> hechos, Map<String, HechoLeido> leidos) {
        return hechos.stream().map(String::trim).map(leidos::get).anyMatch(h -> h != null && "INFERRED".equals(h.grado()))
                ? "INFERRED" : "PROVEN";
    }

    static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
