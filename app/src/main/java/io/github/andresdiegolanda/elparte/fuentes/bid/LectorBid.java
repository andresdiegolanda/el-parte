package io.github.andresdiegolanda.elparte.fuentes.bid;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee los proyectos del BID para Uruguay de su portal de datos abiertos, un CKAN (ADR 0006,
 * docs/fuentes/bid.md). El código de Uruguay ahí es {@code UR}. La tabla de proyectos no tiene datos de
 * personas, así que no hay nada que tapar.
 *
 * <p>Espera {@code elparte.bid.pausa-ms} antes de cada pedido, un segundo por omisión: el robots.txt del
 * portal deja la API abierta y no pide una pausa, pero una noche hace uno o dos pedidos.
 */
@Component
public class LectorBid {

    public static final String FUENTE = "bid";
    /** La tabla de proyectos del BID en su CKAN (https://data.iadb.org/dataset/idb-projects-dataset). */
    public static final String PROYECTOS = "814b7b54-477a-4c25-b3bf-6be05412069d";
    private static final int INTENTOS = 3;

    private final HttpClient http;
    private final JsonMapper json;
    private final String urlBase;
    private final long pausaMs;

    public LectorBid(JsonMapper json, @Value("${elparte.bid.url-base:https://data.iadb.org}") String urlBase,
            @Value("${elparte.bid.pausa-ms:1000}") long pausaMs) {
        this.http = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        this.json = json;
        this.urlBase = urlBase;
        this.pausaMs = pausaMs;
    }

    /** Un proyecto leído, con la huella de la respuesta. */
    public record Leido(JsonNode proyecto, String hashContenido) {
    }

    /** La URL pública de una operación, la que se cita en los hechos. */
    public static String urlPublica(String operacion) {
        return "https://www.iadb.org/en/project/" + operacion;
    }

    public String urlDeBusqueda(String filtros) {
        return urlBase + "/api/3/action/datastore_search?resource_id=" + PROYECTOS + "&limit=100&filters="
                + URLEncoder.encode(filtros, StandardCharsets.UTF_8);
    }

    /** Las operaciones de Uruguay aprobadas un día. La fecha va como la guarda el BID, en texto. */
    public List<Leido> aprobadas(LocalDate dia) {
        return buscar("{\"cntry_cd\":\"UR\",\"apprvl_dt\":\"" + dia + " 00:00:00.000000000\"}");
    }

    /** Una operación, por su número. */
    public Leido operacion(String numero) {
        List<Leido> leidos = buscar("{\"oper_num\":" + json.writeValueAsString(numero) + "}");
        if (leidos.isEmpty()) {
            throw new FallaDelBid("noEncontrado", urlDeBusqueda(numero), "El BID no tiene la operación " + numero);
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
            throw new FallaDelBid("formato", url, "La respuesta no es JSON");
        }
        if (!raiz.path("success").asBoolean(false) || !raiz.path("result").path("records").isArray()) {
            throw new FallaDelBid("formato", url, "La respuesta no trae registros");
        }
        List<Leido> leidos = new ArrayList<>();
        for (JsonNode registro : raiz.path("result").path("records")) {
            leidos.add(new Leido(registro, "sha256:" + Huellas.sha256(json.writeValueAsBytes(registro))));
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
                        .header("User-Agent", "El Parte (proyecto didáctico; https://github.com/andresdiegolanda/el-parte)")
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (respuesta.statusCode() != 200) {
                    throw new FallaDelBid(respuesta.statusCode() >= 500 || respuesta.statusCode() == 429 ? "servidor" : "http", url,
                            "El BID respondió " + respuesta.statusCode());
                }
                return respuesta.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FallaDelBid("sinRespuesta", url, "Interrumpido");
            } catch (java.io.IOException | RuntimeException e) {
                ultima = e instanceof RuntimeException r ? r : new FallaDelBid("sinRespuesta", url, e.getClass().getSimpleName());
                if (e instanceof FallaDelBid falla && "http".equals(falla.tipoDeFalla())) {
                    break;
                }
            }
        }
        if (ultima instanceof FallaDelBid falla) {
            throw falla;
        }
        throw new FallaDelBid("sinRespuesta", url, "Sin respuesta después de " + INTENTOS + " intentos");
    }

    /** Una falla de la fuente, con el tipo de {@code fuente.fallida} (docs/contratos.md). */
    public static class FallaDelBid extends RuntimeException {
        private final String tipoDeFalla;
        private final String url;

        public FallaDelBid(String tipoDeFalla, String url, String detalle) {
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
