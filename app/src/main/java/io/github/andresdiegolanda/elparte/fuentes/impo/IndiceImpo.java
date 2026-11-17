package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import io.github.andresdiegolanda.elparte.fuentes.impo.LectorImpo.FallaDeImpo;

/**
 * El índice de una edición del Diario Oficial: de qué leyes y decretos trae (ADR 0010). El PDF se lee
 * acá y no se guarda; se guarda la lista.
 *
 * <p>Cada documento del índice empieza con su número de orden: {@code 7 - Decreto 223/026.- Reglaméntase
 * la Ley 20.504, ...  (4.083*R)....... Pág. 8}. Las leyes no traen el año en el número: es el de la
 * edición. Las resoluciones de cada organismo no se leen: casi todas son designaciones de personas, y no
 * tienen una dirección fija en el banco de normas.
 */
public final class IndiceImpo {

    private static final Pattern ENTRADA = Pattern.compile(
            "(?m)^\\s*(\\d{1,3}) - (Ley|Decreto) (\\d{1,3}(?:\\.\\d{3})?)(?:/(\\d{3}))?\\.-\\s*([\\s\\S]*?)Pág\\.\\s*\\d+");
    private static final Pattern COLA = Pattern.compile("\\s*\\(\\d[\\d.,]*\\*?\\s*R?\\)[.\\s]*$");

    private IndiceImpo() {
    }

    /** Una ley o un decreto del índice, con su identificador en el banco de normas de IMPO. */
    public record Entrada(int orden, String tipo, String numero, int anio, String sumario) {

        /** {@code leyes-originales/20531-2026} o {@code decretos/223-2026}. */
        public String identificador() {
            return ("Ley".equals(tipo) ? "leyes-originales/" : "decretos/") + numero.replace(".", "") + "-" + anio;
        }

        /** Como lo nombra el Diario Oficial: {@code Ley 20.531} o {@code Decreto 223/026}. */
        public String nombre() {
            return "Ley".equals(tipo) ? "Ley " + numero : "Decreto " + numero + "/" + String.format("%03d", anio % 1000);
        }
    }

    /** Las leyes y los decretos de un índice en PDF, en el orden del índice. */
    public static List<Entrada> leer(byte[] pdf, int anioDeLaEdicion, String url) {
        String texto;
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            texto = new PDFTextStripper().getText(documento);
        } catch (IOException e) {
            throw new FallaDeImpo("formato", url, "El índice no se pudo leer como PDF: " + e.getMessage());
        }
        return deTexto(texto, anioDeLaEdicion);
    }

    static List<Entrada> deTexto(String texto, int anioDeLaEdicion) {
        List<Entrada> entradas = new ArrayList<>();
        Matcher m = ENTRADA.matcher(texto);
        while (m.find()) {
            int anio = m.group(4) == null ? anioDeLaEdicion : 2000 + Integer.parseInt(m.group(4));
            String sumario = COLA.matcher(m.group(5).replaceAll("\\s+", " ").trim()).replaceAll("").trim();
            if (sumario.endsWith(".")) {
                sumario = sumario.substring(0, sumario.length() - 1);
            }
            entradas.add(new Entrada(Integer.parseInt(m.group(1)), m.group(2), m.group(3), anio, sumario));
        }
        return entradas;
    }
}
