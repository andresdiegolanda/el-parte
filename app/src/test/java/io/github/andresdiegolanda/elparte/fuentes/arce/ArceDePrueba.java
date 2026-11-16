package io.github.andresdiegolanda.elparte.fuentes.arce;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Un ARCE de mentira para las pruebas: sirve un feed y unas publicaciones inventadas con la forma de
 * las de verdad (docs/fuentes/compras-estatales.md), y cuenta cuántas veces le piden algo.
 *
 * <p>Todo es sintético. Los nombres de persona son "Fulana de Tal" y "Mengano de Tal", y los RUT no
 * existen. La noche de las pruebas es el 12 de noviembre de 2026:
 *
 * <ul>
 * <li>{@code llamado-1200001}: un llamado del BPS, publicado esa noche.</li>
 * <li>{@code adjudicacion-1200002}: una adjudicación del BPS a una sociedad y a una persona.</li>
 * <li>{@code llamado-1200003}: un llamado de otro organismo, que el explorador descarta.</li>
 * <li>{@code aclaracion_1200004}: una aclaración del BPS publicada el día anterior, en el margen.</li>
 * <li>{@code llamado-1200005}: publicado dos días antes, fuera de la ventana: nunca se pide.</li>
 * <li>{@code llamado-1200006}: el servidor falla al pedirla.</li>
 * <li>{@code adjudicacion-1200007}: una compra del BPS repartida en dos adjudicaciones a dos proveedores.
 * No está en el feed: solo la pide la prueba de los candidatos.</li>
 * </ul>
 *
 * <p>Para la medición contra el banco hay además tres noches de octubre, del 14 al 16, con un día de
 * margen cada una:
 *
 * <ul>
 * <li>{@code llamado-1100001}: un llamado del BPS publicado el 13, en el margen de la primera noche.</li>
 * <li>{@code adjudicacion-1100002}: una adjudicación del BPS del 14, que la segunda noche vuelve a ver.</li>
 * <li>{@code llamado-1100003}: un llamado de otro organismo, del 14.</li>
 * <li>{@code aclaracion_1100004}: una aclaración del BPS del 15, que la tercera noche vuelve a ver.</li>
 * <li>{@code llamado-1100005}: un llamado de otro organismo, del 16.</li>
 * <li>{@code llamado-1100006}: del 16; el servidor falla al pedirla.</li>
 * </ul>
 *
 * <p>Para el presupuesto que corta hay una noche de setiembre, el 10, con tres llamados del BPS
 * ({@code llamado-1300001} a {@code llamado-1300003}): más de lo que alcanza un presupuesto chico.
 */
public class ArceDePrueba implements AutoCloseable {

    public static final String NOCHE = "2026-11-12";
    /** Las noches de octubre para la medición contra el banco. */
    public static final String PRIMERA_NOCHE_DEL_BANCO = "2026-10-14";
    public static final String ULTIMA_NOCHE_DEL_BANCO = "2026-10-16";
    /** La noche de setiembre para el presupuesto que corta. */
    public static final String NOCHE_DEL_PRESUPUESTO = "2026-09-10";

    private static final DateTimeFormatter FECHA_DEL_FEED =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private static final String BPS = """
            {"id": "28-1", "name": "Banco de Previsión Social"}""";

    private static final String PARTE_BPS = """
            {"id": "28-1", "name": "Banco de Previsión Social", "roles": ["buyer", "procuringEntity"],
             "contactPoint": {"name": "Mengano de Tal", "email": "mengano@ejemplo.invalid", "telephone": "0000 0000"}}""";

    private final Map<String, String> publicaciones = new LinkedHashMap<>();
    private final AtomicInteger pedidos = new AtomicInteger();
    private HttpServer servidor;

    public ArceDePrueba() {
        publicaciones.put("llamado-1200001", """
                {"releases": [{
                  "id": "llamado-1200001", "ocid": "ocds-yfs5dr-1200001", "date": "2026-11-12T10:15:00Z",
                  "tag": ["tender"], "buyer": %s, "parties": [%s],
                  "tender": {"id": "1200001", "title": "Compra Directa 900/2026",
                    "description": "Adquisición de papel para impresoras", "status": "active",
                    "items": [{"id": "1", "description": "Papel A4, resma de 500 hojas"}],
                    "tenderPeriod": {"endDate": "2026-11-19T14:00:00Z"},
                    "contactPoint": {"name": "Mengano de Tal", "email": "mengano@ejemplo.invalid"}}
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("adjudicacion-1200002", """
                {"releases": [{
                  "id": "adjudicacion-1200002", "ocid": "ocds-yfs5dr-1200002", "date": "2026-11-12T15:40:00Z",
                  "tag": ["award"], "buyer": %s,
                  "parties": [%s,
                    {"id": "R210000000019", "name": "Distribuidora Ejemplo S.A.", "roles": ["supplier"],
                     "identifier": {"scheme": "UY-RUT", "id": "210000000019"}},
                    {"id": "R110000000012", "name": "Fulana de Tal", "roles": ["supplier"],
                     "identifier": {"scheme": "UY-RUT", "id": "110000000012"},
                     "contactPoint": {"name": "Fulana de Tal", "email": "fulana@ejemplo.invalid"}}],
                  "awards": [{"id": "1200002-1", "status": "active",
                    "suppliers": [{"id": "R210000000019", "name": "Distribuidora Ejemplo S.A."},
                                  {"id": "R110000000012", "name": "Fulana de Tal"}],
                    "items": [{"id": "1", "description": "Tóner negro"}],
                    "value": {"amount": 125000, "currency": "UYU"}}]
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("adjudicacion-1200007", """
                {"releases": [{
                  "id": "adjudicacion-1200007", "ocid": "ocds-yfs5dr-1200007", "date": "2026-11-12T16:00:00Z",
                  "tag": ["award"], "buyer": %s,
                  "parties": [%s,
                    {"id": "R210000000027", "name": "Laboratorio Uno S.A.", "roles": ["supplier"]},
                    {"id": "R210000000035", "name": "Laboratorio Dos S.A.", "roles": ["supplier"]}],
                  "awards": [
                    {"id": "1200007-1", "status": "active",
                     "suppliers": [{"id": "R210000000027", "name": "Laboratorio Uno S.A."}],
                     "items": [{"id": "1", "description": "Ácido fólico"}, {"id": "2", "description": "Vitamina D"}],
                     "value": {"amount": 1000, "currency": "UYU"}},
                    {"id": "1200007-2", "status": "active",
                     "suppliers": [{"id": "R210000000035", "name": "Laboratorio Dos S.A."}],
                     "items": [{"id": "3", "description": "Hierro"}, {"id": "4", "description": "Zinc"}],
                     "value": {"amount": 2000, "currency": "UYU"}}]
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("llamado-1200003", """
                {"releases": [{
                  "id": "llamado-1200003", "ocid": "ocds-yfs5dr-1200003", "date": "2026-11-12T11:00:00Z",
                  "tag": ["tender"], "buyer": {"id": "99-1", "name": "Organismo Inventado"},
                  "tender": {"id": "1200003", "title": "Licitación Abreviada 1/2026", "items": []}
                }]}""");
        publicaciones.put("aclaracion_1200004", """
                {"releases": [{
                  "id": "aclaracion_1200004", "ocid": "ocds-yfs5dr-1200004", "date": "2026-11-11T09:30:00Z",
                  "tag": ["tenderAmendment"], "buyer": %s, "parties": [%s],
                  "tender": {"id": "1200004",
                    "documents": [{"id": "1", "description": "Se aclara el lugar de entrega de la mercadería"}]}
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("llamado-1200005", """
                {"releases": [{"id": "llamado-1200005", "ocid": "ocds-yfs5dr-1200005", "date": "2026-11-10T08:00:00Z",
                  "tag": ["tender"], "buyer": %s, "tender": {"id": "1200005", "title": "Fuera de la ventana"}}]}"""
                .formatted(BPS));

        publicaciones.put("llamado-1100001", """
                {"releases": [{
                  "id": "llamado-1100001", "ocid": "ocds-yfs5dr-1100001", "date": "2026-10-13T10:00:00Z",
                  "tag": ["tender"], "buyer": %s, "parties": [%s],
                  "tender": {"id": "1100001", "title": "Compra Directa 800/2026",
                    "description": "Adquisición de sillas de oficina", "status": "active",
                    "items": [{"id": "1", "description": "Silla ergonómica"}]}
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("adjudicacion-1100002", """
                {"releases": [{
                  "id": "adjudicacion-1100002", "ocid": "ocds-yfs5dr-1100002", "date": "2026-10-14T15:00:00Z",
                  "tag": ["award"], "buyer": %s,
                  "parties": [%s,
                    {"id": "R210000000027", "name": "Mobiliario Ejemplo S.A.", "roles": ["supplier"],
                     "identifier": {"scheme": "UY-RUT", "id": "210000000027"}}],
                  "awards": [{"id": "1100002-1", "status": "active",
                    "suppliers": [{"id": "R210000000027", "name": "Mobiliario Ejemplo S.A."}],
                    "items": [{"id": "1", "description": "Escritorios"}],
                    "value": {"amount": 98000, "currency": "UYU"}}]
                }]}""".formatted(BPS, PARTE_BPS));
        publicaciones.put("llamado-1100003", """
                {"releases": [{
                  "id": "llamado-1100003", "ocid": "ocds-yfs5dr-1100003", "date": "2026-10-14T11:00:00Z",
                  "tag": ["tender"], "buyer": {"id": "99-1", "name": "Organismo Inventado"},
                  "tender": {"id": "1100003", "title": "Licitación Abreviada 2/2026", "items": []}
                }]}""");
        publicaciones.put("aclaracion_1100004", """
                {"releases": [{
                  "id": "aclaracion_1100004", "ocid": "ocds-yfs5dr-1100004", "date": "2026-10-15T09:30:00Z",
                  "tag": ["tenderAmendment"], "buyer": %s, "parties": [%s],
                  "tender": {"id": "1100004",
                    "documents": [{"id": "1", "description": "Se prorroga la fecha de apertura"}]}
                }]}""".formatted(BPS, PARTE_BPS));
        for (int n = 1; n <= 3; n++) {
            publicaciones.put("llamado-130000" + n, """
                    {"releases": [{
                      "id": "llamado-130000%d", "ocid": "ocds-yfs5dr-130000%d", "date": "2026-09-10T1%d:00:00Z",
                      "tag": ["tender"], "buyer": %s, "parties": [%s],
                      "tender": {"id": "130000%d", "title": "Compra Directa 70%d/2026",
                        "description": "Adquisición de papel, lote %d", "status": "active",
                        "items": [{"id": "1", "description": "Resma de papel A4"}]}
                    }]}""".formatted(n, n, n, BPS, PARTE_BPS, n, n, n));
        }
        publicaciones.put("llamado-1100005", """
                {"releases": [{
                  "id": "llamado-1100005", "ocid": "ocds-yfs5dr-1100005", "date": "2026-10-16T10:00:00Z",
                  "tag": ["tender"], "buyer": {"id": "99-1", "name": "Organismo Inventado"},
                  "tender": {"id": "1100005", "title": "Licitación Abreviada 3/2026", "items": []}
                }]}""");
    }

    public ArceDePrueba arrancar() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/ocds/", this::atender);
        servidor.start();
        return this;
    }

    public String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/ocds";
    }

    /** Cuántos pedidos recibió desde que arrancó. */
    public int pedidos() {
        return pedidos.get();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        pedidos.incrementAndGet();
        String ruta = intercambio.getRequestURI().getPath();
        if (ruta.equals("/ocds/rss/2026/11")) {
            responder(intercambio, 200, "application/rss+xml", feed());
        } else if (ruta.equals("/ocds/rss/2026/10")) {
            responder(intercambio, 200, "application/rss+xml", feedDeOctubre());
        } else if (ruta.equals("/ocds/rss/2026/09")) {
            responder(intercambio, 200, "application/rss+xml", feedDeSetiembre());
        } else if (ruta.equals("/ocds/release/llamado-1200006") || ruta.equals("/ocds/release/llamado-1100006")) {
            responder(intercambio, 500, "text/plain", "Error interno");
        } else if (ruta.equals("/ocds/release/roto")) {
            responder(intercambio, 200, "text/html", "<html>Mantenimiento</html>");
        } else if (ruta.startsWith("/ocds/release/") && publicaciones.containsKey(ruta.substring(14))) {
            responder(intercambio, 200, "application/json", publicaciones.get(ruta.substring(14)));
        } else {
            responder(intercambio, 404, "text/plain", "No existe");
        }
    }

    private static String feed() {
        StringBuilder rss = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Compras estatales (prueba)</title>
                """);
        item(rss, "llamado-1200005", LocalDateTime.of(2026, 11, 10, 8, 0), "llamado");
        item(rss, "aclaracion_1200004", LocalDateTime.of(2026, 11, 11, 9, 30), "aclaracion");
        item(rss, "llamado-1200001", LocalDateTime.of(2026, 11, 12, 10, 15), "llamado");
        item(rss, "llamado-1200003", LocalDateTime.of(2026, 11, 12, 11, 0), "llamado");
        item(rss, "llamado-1200006", LocalDateTime.of(2026, 11, 12, 12, 0), "llamado");
        item(rss, "adjudicacion-1200002", LocalDateTime.of(2026, 11, 12, 15, 40), "adjudicacion");
        return rss.append("</channel></rss>\n").toString();
    }

    private static String feedDeOctubre() {
        StringBuilder rss = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Compras estatales (prueba, octubre)</title>
                """);
        item(rss, "llamado-1100001", LocalDateTime.of(2026, 10, 13, 10, 0), "llamado");
        item(rss, "llamado-1100003", LocalDateTime.of(2026, 10, 14, 11, 0), "llamado");
        item(rss, "adjudicacion-1100002", LocalDateTime.of(2026, 10, 14, 15, 0), "adjudicacion");
        item(rss, "aclaracion_1100004", LocalDateTime.of(2026, 10, 15, 9, 30), "aclaracion");
        item(rss, "llamado-1100005", LocalDateTime.of(2026, 10, 16, 10, 0), "llamado");
        item(rss, "llamado-1100006", LocalDateTime.of(2026, 10, 16, 12, 0), "llamado");
        return rss.append("</channel></rss>\n").toString();
    }

    private static String feedDeSetiembre() {
        StringBuilder rss = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Compras estatales (prueba, setiembre)</title>
                """);
        for (int n = 1; n <= 3; n++) {
            item(rss, "llamado-130000" + n, LocalDateTime.of(2026, 9, 10, 10 + n, 0), "llamado");
        }
        return rss.append("</channel></rss>\n").toString();
    }

    private static void item(StringBuilder rss, String id, LocalDateTime publicada, String categoria) {
        rss.append("<item><title>").append(id).append("</title><guid>").append(id).append("</guid><pubDate>")
                .append(FECHA_DEL_FEED.format(publicada)).append("</pubDate><category>").append(categoria)
                .append("</category></item>\n");
    }

    private static void responder(HttpExchange intercambio, int estado, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", tipo + "; charset=utf-8");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @Override
    public void close() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }
}
