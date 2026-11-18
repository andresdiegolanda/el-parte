package io.github.andresdiegolanda.elparte.fuentes.bancomundial;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lee los proyectos del Banco Mundial para Uruguay de la versión 3 de su API de búsqueda, la que usa el
 * propio sitio de proyectos (ADR 0006, docs/fuentes/banco-mundial.md). No está documentada y puede cambiar.
 *
 * <p>Se piden solo los campos de {@link #CAMPOS}, sin el jefe de equipo del banco, y lo que llegue de más
 * se descarta: los términos de la API prohíben juntar o guardar información personal (ADR 0008). Espera
 * {@code elparte.banco-mundial.pausa-ms} antes de cada pedido, dos segundos por omisión: delante hay
 * Cloudflare, que ya respondió 429 a la API del catálogo.
 */
@Component
public class LectorBancoMundial {

    public static final String FUENTE = "banco-mundial";
    /** Los campos que se piden y se guardan de cada proyecto. */
    public static final List<String> CAMPOS = List.of("id", "project_name", "boardapprovaldate", "borrower", "impagency",
            "totalamt", "status", "pdo", "project_abstract");
    private static final int INTENTOS = 3;

    private final HttpClient http;
    private final JsonMapper json;
    private final String urlBase;
    private final long pausaMs;

    public LectorBancoMundial(JsonMapper json,
            @Value("${elparte.banco-mundial.url-base:https://search.worldbank.org}") String urlBase,
            @Value("${elparte.banco-mundial.pausa-ms:2000}") long pausaMs) {
        this.http = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        this.json = json;
        this.urlBase = urlBase;
        this.pausaMs = pausaMs;
    }

    /** Un proyecto leído, solo con los campos pedidos, y la huella de esos campos. */
    public record Leido(JsonNode proyecto, String hashContenido) {
    }

    /** La URL pública de un proyecto, la que se cita en los hechos. */
    public static String urlPublica(String proyecto) {
        return "https://projects.worldbank.org/en/projects-operations/project-detail/" + proyecto;
    }

    String urlDeBusqueda(String filtros) {
        return urlBase + "/api/v3/projects?format=json&rows=100&fl=" + String.join(",", CAMPOS) + "&" + filtros;
    }

    /** Los proyectos de Uruguay aprobados por el directorio un día. */
    public List<Leido> aprobados(LocalDate dia) {
        List<Leido> leidos = buscar("countrycode_exact=UY&strdate=" + dia + "&enddate=" + dia);
        // La API filtra por la fecha de aprobación; igual se mira, por si un día cambia lo que filtra.
        return leidos.stream().filter(l -> l.proyecto().path("boardapprovaldate").asString("").startsWith(dia.toString())).toList();
    }

    /** Un proyecto, por su identificador. */
    public Leido proyecto(String id) {
        if (!id.matches("P\\d+")) {
            throw new FallaDelBancoMundial("noEncontrado", urlDeBusqueda("id=" + id), "No es un identificador de proyecto: " + id);
        }
        List<Leido> leidos = buscar("id=" + id);
        if (leidos.isEmpty()) {
            throw new FallaDelBancoMundial("noEncontrado", urlDeBusqueda("id=" + id), "El Banco Mundial no tiene el proyecto " + id);
        }
        return leidos.getFirst();
    }

    private List<Leido> buscar(String filtros) {
        String url = urlDeBusqueda(filtros);
        byte[] cuerpo = traer(url);
        JsonNode raiz;
        try {
            raiz = json.readTree(cuerpo);
        } catch (RuntimeException e) {
            throw new FallaDelBancoMundial("formato", url, "La respuesta no es JSON");
        }
        if (!raiz.path("projects").isObject()) {
            throw new FallaDelBancoMundial("formato", url, "La respuesta no trae proyectos");
        }
        List<Leido> leidos = new ArrayList<>();
        for (JsonNode proyecto : raiz.path("projects")) {
            ObjectNode acotado = json.createObjectNode();
            for (String campo : CAMPOS) {
                if (proyecto.has(campo)) {
                    acotado.set(campo, proyecto.get(campo));
                }
            }
            leidos.add(new Leido(acotado, "sha256:" + Huellas.sha256(json.writeValueAsBytes(acotado))));
        }
        return leidos;
    }

    private byte[] traer(String url) {
        RuntimeException ultima = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                if (pausaMs > 0) {
                    Thread.sleep(pausaMs * intento);
                }
                HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "El Parte (proyecto didactico; https://github.com/andresdiegolanda/el-parte)")
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (respuesta.statusCode() != 200) {
                    throw new FallaDelBancoMundial(respuesta.statusCode() >= 500 || respuesta.statusCode() == 429 ? "servidor" : "http",
                            url, "El Banco Mundial respondió " + respuesta.statusCode());
                }
                return respuesta.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FallaDelBancoMundial("sinRespuesta", url, "Interrumpido");
            } catch (java.io.IOException | RuntimeException e) {
                ultima = e instanceof RuntimeException r ? r
                        : new FallaDelBancoMundial("sinRespuesta", url, e.getClass().getSimpleName());
                if (e instanceof FallaDelBancoMundial falla && "http".equals(falla.tipoDeFalla())) {
                    break;
                }
            }
        }
        if (ultima instanceof FallaDelBancoMundial falla) {
            throw falla;
        }
        throw new FallaDelBancoMundial("sinRespuesta", url, "Sin respuesta después de " + INTENTOS + " intentos");
    }

    /** Una falla de la fuente, con el tipo de {@code fuente.fallida} (docs/contratos.md). */
    public static class FallaDelBancoMundial extends RuntimeException {
        private final String tipoDeFalla;
        private final String url;

        public FallaDelBancoMundial(String tipoDeFalla, String url, String detalle) {
            super(detalle);
            this.tipoDeFalla = tipoDeFalla;
            this.url = url;
        }

        public String tipoDeFalla() {
            return tipoDeFalla;
        }

        public String url() {
            return url;
        }
    }
}
