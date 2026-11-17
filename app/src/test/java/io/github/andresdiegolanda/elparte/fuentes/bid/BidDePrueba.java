package io.github.andresdiegolanda.elparte.fuentes.bid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * Un BID de mentira para las pruebas: responde como la tabla de proyectos de su CKAN (docs/fuentes/bid.md).
 * Las operaciones no existen. El 6 de noviembre de 2026 se aprobaron dos: {@code UR-L9001}, que nombra al
 * BPS por su sigla, y {@code UR-T9002}, que no nombra a ninguna entidad seguida. Cualquier otro día, nada.
 */
public class BidDePrueba implements AutoCloseable {

    public static final String NOCHE = "2026-11-06";

    private static final String L9001 = """
            {"_id": 1, "oper_num": "UR-L9001", "oper_nm": "Program to Modernize Social Security Services",
             "apprvl_dt": "2026-11-06 00:00:00.000000000", "sign_dt": "", "cntry_cd": "UR", "opertyp_nm": "Loan Operation",
             "objtv": "The objective is to improve the benefits management of the Social Security Bank (BPS) through a new system."}
            """;
    private static final String T9002 = """
            {"_id": 2, "oper_num": "UR-T9002", "oper_nm": "Support to a Test Sanitation Program",
             "apprvl_dt": "2026-11-06 00:00:00.000000000", "sign_dt": "", "cntry_cd": "UR", "opertyp_nm": "Technical Cooperation",
             "objtv": "The objective is to support a municipality in sanitation."}
            """;

    private final AtomicInteger pedidos = new AtomicInteger();
    private HttpServer servidor;

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
        servidor.createContext("/api/3/action/datastore_search", intercambio -> {
            pedidos.incrementAndGet();
            String consulta = URLDecoder.decode(intercambio.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String registros;
            if (consulta.contains("\"apprvl_dt\":\"" + NOCHE)) {
                registros = L9001 + "," + T9002;
            } else if (consulta.contains("\"oper_num\":\"UR-L9001\"")) {
                registros = L9001;
            } else {
                registros = "";
            }
            byte[] cuerpo = ("{\"success\": true, \"result\": {\"records\": [" + registros + "]}}").getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @Override
    public void close() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }
}
