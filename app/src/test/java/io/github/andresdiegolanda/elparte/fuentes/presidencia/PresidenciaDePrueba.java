package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Una Presidencia de mentira para las pruebas: responde como el listado de la normativa de gub.uy y las
 * páginas de cada resolución (docs/fuentes/presidencia.md). Las resoluciones y las personas son inventadas.
 *
 * <p>Noviembre de 2026 tiene 25 resoluciones en tres páginas, de la más nueva a la más vieja: diez del 5,
 * seis del 4, seis del 3 y tres del 2. De las seis del 3, una designa a una persona en el directorio del
 * BPS, con su nombre en el título, en el resumen y en la ruta; las otras no nombran a ninguna entidad
 * seguida. Cualquier otro mes no tiene resoluciones.
 */
public class PresidenciaDePrueba implements AutoCloseable {

    public static final String NOCHE = "2026-11-03";
    /** El nombre inventado de la persona designada, que no tiene que quedar guardado en ningún lado. */
    public static final String NOMBRE = "Pedro Ficticio";
    public static final String NODO = "990001";
    static final String RUTA_DEL_BPS = "/presidencia/institucional/normativa/resolucion-sn026-se-designa-pedro-ficticio-directorio-banco";
    private static final String TITULO_DEL_BPS = "Resolución S/N/026 Se designa a " + NOMBRE
            + " como integrante del Directorio del Banco de Previsión Social";
    private static final String RESUMEN_DEL_BPS = "Se designa al señor " + NOMBRE
            + " como integrante del Directorio del Banco de Previsión Social, en representación del Poder Ejecutivo.";

    private record Item(String fecha, String titulo, String resumen, String ruta) {
    }

    private final List<Item> noviembre = new ArrayList<>();
    private final AtomicInteger pedidos = new AtomicInteger();
    private HttpServer servidor;

    public PresidenciaDePrueba() {
        for (int i = 1; i <= 10; i++) {
            noviembre.add(otra("05/11/2026", i));
        }
        for (int i = 11; i <= 16; i++) {
            noviembre.add(otra("04/11/2026", i));
        }
        for (int i = 17; i <= 21; i++) {
            noviembre.add(otra("03/11/2026", i));
        }
        noviembre.add(new Item("03/11/2026", TITULO_DEL_BPS, RESUMEN_DEL_BPS.substring(0, 60) + "...", RUTA_DEL_BPS));
        for (int i = 23; i <= 25; i++) {
            noviembre.add(otra("02/11/2026", i));
        }
    }

    private static Item otra(String fecha, int numero) {
        return new Item(fecha, "Resolución S/N/026 Se designa en misión oficial a Ana Inventada " + numero,
                "Se designa en misión oficial a Ana Inventada " + numero + ".",
                "/presidencia/institucional/normativa/resolucion-sn026-mision-oficial-ana-inventada-" + numero);
    }

    public synchronized String urlBase() {
        if (servidor == null) {
            arrancar();
        }
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    public int pedidos() {
        return pedidos.get();
    }

    private void arrancar() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/presidencia/", intercambio -> {
            pedidos.incrementAndGet();
            String camino = intercambio.getRequestURI().getPath();
            String consulta = intercambio.getRequestURI().getRawQuery() == null ? ""
                    : URLDecoder.decode(intercambio.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            if (camino.equals("/presidencia/institucional/normativa")) {
                responder(intercambio, 200, listado(consulta));
            } else if (camino.equals(RUTA_DEL_BPS) || camino.equals("/presidencia/node/" + NODO)) {
                responder(intercambio, 200, pagina());
            } else {
                responder(intercambio, 404, "<html><body>No encontrada</body></html>");
            }
        });
        servidor.start();
    }

    private String listado(String consulta) {
        boolean esNoviembre = consulta.contains("field_tipo_de_norma_target_id=276") && consulta.contains("year=2026")
                && consulta.contains("month=11");
        int pagina = 0;
        for (String parte : consulta.split("&")) {
            if (parte.startsWith("page=")) {
                pagina = Integer.parseInt(parte.substring(5));
            }
        }
        StringBuilder html = new StringBuilder("<html><body><main><h2 class=\"Page-title\"> Normativa</h2><ul class=\"List List--media\">");
        List<Item> items = esNoviembre ? noviembre.subList(Math.min(pagina * 10, noviembre.size()),
                Math.min(pagina * 10 + 10, noviembre.size())) : List.of();
        for (Item item : items) {
            html.append("<li class=\"Media\"><div class=\"Media-body\"><div> <span class=\"Box-info\">").append(item.fecha())
                    .append("</span></div><h3> <a href=\"").append(item.ruta()).append("\" rel=\"bookmark\"> ").append(item.titulo())
                    .append("</a></h3><div><p>").append(item.resumen()).append("</p></div></div></li>");
        }
        html.append("</ul>");
        if (!items.isEmpty()) {
            html.append("<div class=\"Pagination\"><div class=\"Pagination-text center\">Mostrando 1 - 10 de ")
                    .append(noviembre.size()).append(" resultados</div></div>");
        }
        return html.append("</main></body></html>").toString();
    }

    private static String pagina() {
        return "<html><head><script type=\"application/json\" data-drupal-selector=\"drupal-settings-json\">"
                + "{\"path\":{\"baseUrl\":\"\\/\",\"currentPath\":\"node\\/" + NODO + "\"}}</script></head><body><main>"
                + "<article><div class=\"Page-document\"><h2 class=\"Page-Title\">" + TITULO_DEL_BPS + "</h2>"
                + "<div class=\"Page-info\"><div class=\"Bar\"><div class=\"Bar-cell\" ><div class=\"Page-date\" >03/11/2026</div></div></div></div>"
                + "<div class=\"Page-description\"> " + RESUMEN_DEL_BPS + "</div>"
                + "<p><a href=\"https://medios.presidencia.gub.uy/legal/2026/resoluciones/11/presidencia_ps_9001.pdf\">Ver resolución</a></p>"
                + "</div></article></main></body></html>";
    }

    private static void responder(HttpExchange intercambio, int estado, String html) throws IOException {
        byte[] cuerpo = html.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
        intercambio.close();
    }

    @Override
    public void close() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }
}
