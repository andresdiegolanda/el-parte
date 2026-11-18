package io.github.andresdiegolanda.elparte.fuentes.bancomundial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * Un Banco Mundial de mentira para las pruebas: responde como la versión 3 de su API de proyectos
 * (docs/fuentes/banco-mundial.md). Los proyectos no existen. El 4 de noviembre de 2026 el directorio aprobó
 * dos: {@code P900001}, cuya agencia ejecutora es el BPS, y {@code P900002}, que no nombra a ninguna
 * entidad seguida. Los dos traen el nombre del jefe de equipo, que el lector no tiene que guardar.
 * Cualquier otro día, nada.
 */
public class BancoMundialDePrueba implements AutoCloseable {

    public static final String NOCHE = "2026-11-04";
    public static final String JEFE_DE_EQUIPO = "Persona Inventada";

    private static final String P900001 = """
            "P900001": {"id": "P900001", "proj_id": "P900001", "project_name": "Uruguay Social Protection Modernization Project",
             "boardapprovaldate": "2026-11-04T00:00:00Z", "borrower": "Oriental Republic of Uruguay",
             "impagency": "Banco de Prevision Social (BPS)", "totalamt": "50000000", "status": "Active",
             "pdo": "To improve the delivery of social security benefits.", "teamleadname": "%s",
             "project_abstract": "The project will support a new benefits management system for the Social Security Bank."}
            """.formatted(JEFE_DE_EQUIPO);
    private static final String P900002 = """
            "P900002": {"id": "P900002", "project_name": "Uruguay Test Roads Project", "boardapprovaldate": "2026-11-04T00:00:00Z",
             "borrower": "Oriental Republic of Uruguay", "impagency": "Ministry of Transport and Public Works",
             "totalamt": "100000000", "status": "Active", "pdo": "To improve roads.", "teamleadname": "%s"}
            """.formatted(JEFE_DE_EQUIPO);

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
        servidor.createContext("/api/v3/projects", intercambio -> {
            pedidos.incrementAndGet();
            String consulta = URLDecoder.decode(intercambio.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String proyectos;
            if (consulta.contains("strdate=" + NOCHE) && consulta.contains("enddate=" + NOCHE)) {
                proyectos = P900001 + "," + P900002;
            } else if (consulta.contains("id=P900001")) {
                proyectos = P900001;
            } else {
                proyectos = "";
            }
            byte[] cuerpo = ("{\"rows\": 100, \"os\": \"0\", \"page\": \"1\", \"projects\": {" + proyectos + "}, \"facets\": {}}")
                    .getBytes(StandardCharsets.UTF_8);
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
