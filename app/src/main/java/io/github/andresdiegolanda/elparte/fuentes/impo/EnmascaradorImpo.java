package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tapa los datos personales de una norma de IMPO antes de que la vea nadie (ADR 0008, ADR 0010): saca
 * los firmantes y cambia por {@code [persona]} los nombres que siguen a un título o a un tratamiento
 * ("Sra.", "Dr.", "Esc.", "señor", "ciudadana"). Un nombre sin título ni tratamiento no se ve: es el
 * límite de tapar sin el modelo, y queda dicho en docs/fuentes/impo.md.
 */
public final class EnmascaradorImpo {

    static final String PERSONA = "[persona]";
    private static final String PALABRA = "[A-ZÁÉÍÓÚÜÑ][\\p{L}'´-]+";
    /** Cargos que siguen a un tratamiento y no son nombres: "el señor Ministro", "Sr. Presidente". */
    private static final String CARGO = "(?!(?:Presidente|Presidenta|Vicepresidente|Vicepresidenta|Ministr[oa]s?|Subsecretari[oa]s?"
            + "|Secretari[oa]s?|Director(?:a|es)?|Intendente|Senador(?:a|es)?|Diputad[oa]s?|Fiscal|Juez|Jueza|Gerente|Jef[ea]"
            + "|Encargad[oa]|Representante|Delegad[oa]|Comisionad[oa])\\b)";
    private static final String NOMBRE = CARGO + PALABRA + "(?:\\s+(?:(?:de|del|la|las|los|da|das|do|dos|y|e)\\s+)?" + PALABRA + "){0,5}";
    private static final Pattern CON_TITULO = Pattern.compile(
            "\\b(?:Sr|Sra|Srta|Sres|Sras|Dr|Dra|Dres|Lic|Esc|Cr|Cra|Ing|Arq|Prof|Mtro|Mtra|Psic|Ec|Q\\.F|Tec|Téc|Gral|Cnel|Tte|My|Cap)\\.\\s*(?:en [\\p{L}.]+\\s+)?"
                    + NOMBRE);
    private static final Pattern CON_TRATAMIENTO = Pattern.compile(
            "\\b((?:[Ss]eñor(?:a|ita|es)?|[Cc]iudadan[oa]s?|[Dd]oña|[Dd]on)\\s+)" + NOMBRE);
    private static final String[] CAMPOS = {"nombreNorma", "vistos", "tituloDiar", "referenciasNorma"};

    private EnmascaradorImpo() {
    }

    public record Enmascarada(JsonNode norma, int tapados) {
    }

    public static Enmascarada enmascarar(JsonNode original) {
        ObjectNode norma = (ObjectNode) original.deepCopy();
        int tapados = 0;
        if (norma.has("firmantes")) {
            norma.remove("firmantes");
            tapados++;
        }
        for (String campo : CAMPOS) {
            if (norma.path(campo).isString()) {
                Tapado t = tapar(norma.path(campo).asString());
                norma.put(campo, t.texto());
                tapados += t.tapados();
            }
        }
        if (norma.path("articulos") instanceof ArrayNode articulos) {
            for (JsonNode articulo : articulos) {
                if (articulo instanceof ObjectNode objeto && objeto.path("textoArticulo").isString()) {
                    Tapado t = tapar(objeto.path("textoArticulo").asString());
                    objeto.put("textoArticulo", t.texto());
                    tapados += t.tapados();
                }
            }
        }
        return new Enmascarada(norma, tapados);
    }

    public record Tapado(String texto, int tapados) {
    }

    /** Tapa los nombres de personas de un texto. */
    public static Tapado tapar(String texto) {
        int[] cuenta = {0};
        String salida = reemplazar(CON_TITULO, texto, m -> PERSONA, cuenta);
        salida = reemplazar(CON_TRATAMIENTO, salida, m -> m.group(1) + PERSONA, cuenta);
        return new Tapado(salida, cuenta[0]);
    }

    private static String reemplazar(Pattern patron, String texto, java.util.function.Function<Matcher, String> por,
            int[] cuenta) {
        Matcher m = patron.matcher(texto);
        StringBuilder salida = new StringBuilder();
        while (m.find()) {
            cuenta[0]++;
            m.appendReplacement(salida, Matcher.quoteReplacement(por.apply(m)));
        }
        m.appendTail(salida);
        return salida.toString();
    }
}
