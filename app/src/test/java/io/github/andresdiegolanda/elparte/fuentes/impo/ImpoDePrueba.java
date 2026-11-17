package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.sun.net.httpserver.HttpServer;

/**
 * Un IMPO de mentira para las pruebas: sirve el índice de una edición, armado en PDF al arrancar, y el
 * JSON de sus normas, en ISO-8859-1 como el de verdad (docs/fuentes/impo.md). Todo es sintético: las
 * normas no existen, y las personas son "Fulana de Tal" y "Mengano de Tal".
 *
 * <p>La edición es la del 5 de noviembre de 2026. Trae:
 * <ul>
 * <li>{@code Ley 20.999}: una ley que no nombra al BPS.</li>
 * <li>{@code Decreto 300/026}: un decreto que nombra al BPS en los vistos y en un artículo, con firmantes y
 * con una persona designada en otro artículo.</li>
 * <li>{@code Decreto 301/026}: el servidor falla al pedirlo.</li>
 * <li>Una resolución, que el explorador no lee.</li>
 * </ul>
 * Cualquier otro día no hubo edición: responde 200 con un texto de error, como IMPO.
 */
public class ImpoDePrueba implements AutoCloseable {

    public static final String EDICION = "2026-11-05";

    private static final String DECRETO_300 = """
            {"tipoNorma": "Decreto", "nroNorma": "300", "anioNorma": 2026,
             "nombreNorma": "REGLAMENTACION DEL REGIMEN DE PRUEBA ANTE EL BANCO DE PREVISION SOCIAL\\r\\n",
             "fechaPromulgacion": "03/11/2026", "fechaPublicacion": "05/11/2026",
             "vistos": "VISTO: el régimen de prueba que administra el Banco de Previsión Social;\\r\\nEL PRESIDENTE DE LA REPÚBLICA\\r\\nDECRETA:",
             "articulos": [
               {"nroArticulo": "1", "textoArticulo": "El Banco de Previsión Social otorgará el beneficio de prueba a partir del 1° de enero de 2027."},
               {"nroArticulo": "2", "textoArticulo": "Desígnase a la Sra. Fulana de Tal como responsable del régimen de prueba."},
               {"nroArticulo": "3", "textoArticulo": "Comuníquese, publíquese."}
             ],
             "firmantes": "MENGANO DE TAL - FULANA DE TAL"}
            """;
    private static final String LEY_20999 = """
            {"tipoNorma": "Ley", "nroNorma": "20999", "anioNorma": 2026,
             "nombreNorma": "DECLARACION DE INTERES DE UNA FIESTA DE PRUEBA\\r\\n", "fechaPublicacion": "05/11/2026",
             "vistos": "", "articulos": [{"nroArticulo": "1", "textoArticulo": "Declárase de interés nacional la fiesta de prueba."}],
             "firmantes": "MENGANO DE TAL"}
            """;

    private final Map<String, AtomicInteger> pedidos = new ConcurrentHashMap<>();
    private HttpServer servidor;

    public synchronized String urlBase() {
        if (servidor == null) {
            arrancar();
        }
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    public int pedidos(String ruta) {
        return pedidos.getOrDefault(ruta, new AtomicInteger()).get();
    }

    private void arrancar() {
        byte[] indice = indice();
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            pedidos.computeIfAbsent(ruta, k -> new AtomicInteger()).incrementAndGet();
            byte[] cuerpo;
            int estado = 200;
            String tipo = "application/json; charset=ISO-8859-1";
            if (ruta.equals("/diariooficial/2026/11/05/indice.pdf")) {
                cuerpo = indice;
                tipo = "application/pdf";
            } else if (ruta.startsWith("/diariooficial/")) {
                cuerpo = "Error: no se encontró la edición solicitada".getBytes(StandardCharsets.ISO_8859_1);
                tipo = "text/html; charset=ISO-8859-1";
            } else if (ruta.equals("/bases/decretos/300-2026")) {
                cuerpo = DECRETO_300.getBytes(StandardCharsets.ISO_8859_1);
            } else if (ruta.equals("/bases/leyes-originales/20999-2026")) {
                cuerpo = LEY_20999.getBytes(StandardCharsets.ISO_8859_1);
            } else {
                estado = 500;
                cuerpo = "error".getBytes(StandardCharsets.ISO_8859_1);
            }
            intercambio.getResponseHeaders().add("Content-Type", tipo);
            intercambio.sendResponseHeaders(estado, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    /** El índice de la edición, en PDF, con las líneas como las pone IMPO. */
    static byte[] indice() {
        List<String> lineas = List.of(
                "Índice Nº 32.999 - noviembre 5 de 2026 | DiarioOficial",
                "PODER LEGISLATIVO",
                "1 - Ley 20.999.- Declárase de interés nacional la fiesta de prueba.",
                "(5.001)...................................................................... Pág. 3",
                "PODER EJECUTIVO",
                "MINISTERIO DE TRABAJO Y SEGURIDAD SOCIAL",
                "2 - Decreto 300/026.- Reglaméntase el régimen de prueba ante el Banco de",
                "Previsión Social. (5.002*R).............................................. Pág. 4",
                "3 - Decreto 301/026.- Fíjanse los valores de prueba. (5.003)........ Pág. 5",
                "SERVICIOS DESCENTRALIZADOS",
                "4 - Resolución 4.000/026.- Desígnase a la Sra. Fulana de Tal en una función.",
                "(5.004).............................................................. Pág. 6");
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                contenido.newLineAtOffset(40, 740);
                for (String linea : lineas) {
                    contenido.showText(linea);
                    contenido.newLineAtOffset(0, -14);
                }
                contenido.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }
}
