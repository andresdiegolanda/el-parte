package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.github.andresdiegolanda.elparte.fuentes.impo.IndiceImpo.Entrada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Evidencia;
import tools.jackson.databind.JsonNode;

/**
 * Arma el candidato de una norma que nombra a una entidad seguida, sin el modelo. El resumen sale del
 * índice de la edición y de la norma; la evidencia, de los campos de la norma que la nombran. Un hecho
 * por norma: {@code impo:decretos-223-2026:norma}.
 */
public final class CandidatosDeImpo {

    private static final DateTimeFormatter FECHA_DE_IMPO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int CONTEXTO = 300;

    private CandidatosDeImpo() {
    }

    public static Candidato armar(String candidatoId, Entrada entrada, JsonNode norma, List<NombresDeEntidad> entidades,
            int tapados, String hashContenido, LocalDate edicion) {
        String fecha = fechaDePublicacion(norma, edicion);
        String sumario = EnmascaradorImpo.tapar(entrada.sumario()).texto();
        String articulo = "Ley".equals(entrada.tipo()) ? "la " : "el ";
        String nombres = String.join(", ", entidades.stream().map(NombresDeEntidad::nombre).toList());
        String resumen = "El Diario Oficial del " + fecha + " publicó " + articulo + entrada.nombre() + ": " + sumario
                + ". Nombra a: " + nombres + ".";
        StringBuilder campos = new StringBuilder("nombreNorma = ").append(NormaRecortada.texto(norma.path("nombreNorma")))
                .append("\nfechaPublicacion = ").append(norma.path("fechaPublicacion").asString(""));
        for (NombresDeEntidad entidad : entidades) {
            campos.append("\n").append(dondeLaNombra(norma, entidad));
        }
        return new Candidato(candidatoId, LectorImpo.FUENTE, entrada.identificador(),
                entrada.identificador().replace('/', '-'), LectorImpo.urlPublica(entrada.identificador()), fecha, "norma",
                entidades.stream().map(e -> new EntidadEnCandidato("nombrada", e.entidadId(), e.nombre())).toList(),
                resumen.replaceAll("\\s+", " ").trim(), new Evidencia(campos.toString(), hashContenido), tapados);
    }

    /** La fecha de publicación que da la norma, en ISO; si no la da, la de la edición. */
    static String fechaDePublicacion(JsonNode norma, LocalDate edicion) {
        try {
            return LocalDate.parse(norma.path("fechaPublicacion").asString("").trim(), FECHA_DE_IMPO).toString();
        } catch (RuntimeException e) {
            return edicion.toString();
        }
    }

    /** El primer campo que nombra a la entidad, con un poco de texto alrededor. */
    static String dondeLaNombra(JsonNode norma, NombresDeEntidad entidad) {
        String nombre = NormaRecortada.texto(norma.path("nombreNorma"));
        if (entidad.aparece(nombre)) {
            return "nombreNorma = " + nombre;
        }
        String vistos = NormaRecortada.texto(norma.path("vistos"));
        int enVistos = entidad.dondeAparece(vistos);
        if (enVistos >= 0) {
            return "vistos = " + alrededor(vistos, enVistos);
        }
        for (JsonNode articulo : norma.path("articulos")) {
            String texto = NormaRecortada.texto(articulo.path("textoArticulo"));
            int donde = entidad.dondeAparece(texto);
            if (donde >= 0) {
                return "artículo " + articulo.path("nroArticulo").asString("") + " = " + alrededor(texto, donde);
            }
        }
        return "";
    }

    private static String alrededor(String texto, int donde) {
        int desde = Math.max(0, donde - CONTEXTO / 2);
        int hasta = Math.min(texto.length(), donde + CONTEXTO);
        return (desde > 0 ? "..." : "") + texto.substring(desde, hasta).replaceAll("\\s+", " ").trim()
                + (hasta < texto.length() ? "..." : "");
    }
}
