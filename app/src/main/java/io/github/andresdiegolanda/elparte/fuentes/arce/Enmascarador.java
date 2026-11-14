package io.github.andresdiegolanda.elparte.fuentes.arce;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tapa los datos personales de una publicación de ARCE antes de cualquier otra cosa (ADR 0008):
 *
 * <ul>
 * <li>Descarta todos los campos {@code contactPoint} (nombre, correo y teléfono del contacto).</li>
 * <li>Tapa el nombre y el RUT de un proveedor cuando no se ve que sea una persona jurídica. Un nombre
 * sin marca de sociedad ("S.A.", "S.R.L.", "Ltda.", etc.) puede ser una empresa unipersonal, es decir,
 * una persona: ante la duda, se tapa.</li>
 * </ul>
 */
public final class Enmascarador {

    public static final String PERSONA = "[persona]";

    private static final Pattern PERSONA_JURIDICA = Pattern.compile(
            "\\b(S\\.?\\s?A\\.?(\\s?S\\.?)?|S\\.?\\s?R\\.?\\s?L\\.?|LTDA\\.?|LIMITADA|SOCIEDAD|COOPERATIVA|"
                    + "FUNDACI[OÓ]N|ASOCIACI[OÓ]N|S\\.?\\s?C\\.?|INC\\.?|LLC|CORP\\.?|COMPA[NÑ][IÍ]A|CIA\\.?|"
                    + "UNIVERSIDAD|INTENDENCIA|MINISTERIO|BANCO|ADMINISTRACI[OÓ]N|INSTITUTO|SINDICATO|CONSORCIO|"
                    + "SUCESI[OÓ]N|EMPRESA|GRUPO)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Enmascarador() {
    }

    /** Una publicación ya enmascarada y cuántos datos personales se taparon. */
    public record Enmascarada(JsonNode publicacion, int tapados) {
    }

    public static boolean pareceSociedad(String nombre) {
        return nombre != null && PERSONA_JURIDICA.matcher(nombre).find();
    }

    public static Enmascarada enmascarar(JsonNode publicacion) {
        JsonNode copia = publicacion.deepCopy();
        int[] tapados = { 0 };
        quitarContactos(copia, tapados);
        List<String> rutsTapados = new ArrayList<>();
        for (JsonNode adjudicacion : copia.path("awards")) {
            for (JsonNode proveedor : adjudicacion.path("suppliers")) {
                if (proveedor instanceof ObjectNode objeto && !pareceSociedad(objeto.path("name").asString(""))) {
                    rutsTapados.add(objeto.path("id").asString(""));
                    objeto.put("name", PERSONA);
                    objeto.put("id", PERSONA);
                    tapados[0]++;
                }
            }
        }
        for (JsonNode parte : copia.path("parties")) {
            if (parte instanceof ObjectNode objeto && esProveedor(objeto)
                    && (rutsTapados.contains(objeto.path("id").asString("")) || !pareceSociedad(objeto.path("name").asString("")))) {
                objeto.put("name", PERSONA);
                objeto.put("id", PERSONA);
                objeto.remove("identifier");
            }
        }
        return new Enmascarada(copia, tapados[0]);
    }

    private static boolean esProveedor(ObjectNode parte) {
        for (JsonNode rol : parte.path("roles")) {
            if ("supplier".equals(rol.asString(""))) {
                return true;
            }
        }
        return false;
    }

    private static void quitarContactos(JsonNode nodo, int[] tapados) {
        if (nodo instanceof ObjectNode objeto) {
            if (objeto.remove("contactPoint") != null) {
                tapados[0]++;
            }
            objeto.properties().forEach(campo -> quitarContactos(campo.getValue(), tapados));
        } else if (nodo instanceof ArrayNode lista) {
            lista.forEach(elemento -> quitarContactos(elemento, tapados));
        }
    }
}
