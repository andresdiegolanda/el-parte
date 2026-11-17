package io.github.andresdiegolanda.elparte.fuentes.impo;

import io.github.andresdiegolanda.elparte.fuentes.NombresDeEntidad;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lo que el refutador le muestra al modelo de una norma ya tapada: sus datos, los vistos y los artículos
 * que nombran a las entidades del candidato. Una ley de Rendición de Cuentas tiene cientos de artículos;
 * el pedido no puede crecer sin límite, y lo que importa es lo que dice de la entidad.
 */
public final class NormaRecortada {

    static final int MAXIMO_ARTICULOS = 12;
    static final int MAXIMO_TEXTO = 4000;
    private static final String[] DATOS = {"tipoNorma", "nroNorma", "anioNorma", "nombreNorma", "fechaPromulgacion",
            "fechaPublicacion"};

    private NormaRecortada() {
    }

    public static ObjectNode recortar(JsonNode norma, List<NombresDeEntidad> entidades, JsonMapper json) {
        ObjectNode recorte = json.createObjectNode();
        for (String campo : DATOS) {
            if (norma.has(campo)) {
                recorte.set(campo, norma.get(campo));
            }
        }
        recorte.put("vistos", corto(texto(norma.path("vistos"))));
        ArrayNode articulos = json.createArrayNode();
        int total = 0;
        int mencionan = 0;
        for (JsonNode articulo : norma.path("articulos")) {
            total++;
            String texto = texto(articulo.path("textoArticulo"));
            if (entidades.stream().anyMatch(e -> e.aparece(texto))) {
                mencionan++;
                if (articulos.size() < MAXIMO_ARTICULOS) {
                    articulos.add(json.createObjectNode().put("nroArticulo", articulo.path("nroArticulo").asString(""))
                            .put("textoArticulo", corto(texto)));
                }
            }
        }
        if (articulos.isEmpty()) {
            // Si ningún artículo nombra a la entidad, la nombran los vistos o el nombre: van los primeros.
            for (JsonNode articulo : norma.path("articulos")) {
                if (articulos.size() >= 3) {
                    break;
                }
                articulos.add(json.createObjectNode().put("nroArticulo", articulo.path("nroArticulo").asString(""))
                        .put("textoArticulo", corto(texto(articulo.path("textoArticulo")))));
            }
        }
        recorte.put("articulosEnTotal", total);
        recorte.put("articulosQueNombranALaEntidad", mencionan);
        recorte.set("articulos", articulos);
        return recorte;
    }

    static String texto(JsonNode nodo) {
        return nodo.isString() ? nodo.asString().replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim() : "";
    }

    private static String corto(String texto) {
        return texto.length() <= MAXIMO_TEXTO ? texto : texto.substring(0, MAXIMO_TEXTO) + " [...]";
    }
}
