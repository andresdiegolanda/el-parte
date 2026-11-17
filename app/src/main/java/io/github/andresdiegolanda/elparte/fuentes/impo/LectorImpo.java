package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el Diario Oficial de IMPO (ADR 0010, docs/fuentes/impo.md): el índice de cada edición, en PDF, y
 * el JSON de cada norma. No guarda nada ni tapa nada: eso lo hacen el explorador y el refutador.
 *
 * <p>Espera {@code elparte.impo.pausa-ms} entre un pedido y el siguiente, diez segundos por omisión,
 * como pide el robots.txt de IMPO. La pausa es una sola para toda la aplicación: el explorador y el
 * refutador no le piden a IMPO más rápido que eso, aunque pidan a la vez.
 */
@Component
public class LectorImpo {

    public static final String FUENTE = "impo";
    private static final String URL_PUBLICA = "https://www.impo.com.uy";
    private static final int INTENTOS = 3;

    private final HttpClient http;
    private final JsonMapper json;
    private final String urlBase;
    private final long pausaMs;
    private long ultimoPedido;

    public LectorImpo(JsonMapper json, @Value("${elparte.impo.url-base:https://www.impo.com.uy}") String urlBase,
            @Value("${elparte.impo.pausa-ms:10000}") long pausaMs) {
        this.http = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.json = json;
        this.urlBase = urlBase;
        this.pausaMs = pausaMs;
    }

    /** Una norma leída: su JSON (sin tapar) y la huella de lo que respondió IMPO. */
    public record Leida(JsonNode norma, String hashContenido) {
    }

    public String urlDelIndice(LocalDate dia) {
        return urlBase + String.format("/diariooficial/%d/%02d/%02d/indice.pdf", dia.getYear(), dia.getMonthValue(),
                dia.getDayOfMonth());
    }

    public String urlDeNorma(String identificador) {
        return urlBase + "/bases/" + identificador + "?json=true";
    }

    /** La URL pública de una norma, la que se cita en los hechos, como pide la licencia (ADR 0010). */
    public static String urlPublica(String identificador) {
        return URL_PUBLICA + "/bases/" + identificador;
    }

    /**
     * El índice de la edición de un día, en PDF. Vacío si ese día no hubo edición: IMPO responde 200 con
     * un texto de error, y no un PDF.
     */
    public Optional<byte[]> indice(LocalDate dia) {
        byte[] cuerpo = traer(urlDelIndice(dia));
        boolean esPdf = cuerpo.length > 4 && cuerpo[0] == '%' && cuerpo[1] == 'P' && cuerpo[2] == 'D' && cuerpo[3] == 'F';
        return esPdf ? Optional.of(cuerpo) : Optional.empty();
    }

    /** El JSON de una norma, por su identificador en el banco de IMPO: {@code decretos/223-2026}. Viene en ISO-8859-1. */
    public Leida norma(String identificador) {
        String url = urlDeNorma(identificador);
        byte[] cuerpo = traer(url);
        JsonNode norma;
        try {
            norma = json.readTree(new String(cuerpo, StandardCharsets.ISO_8859_1));
        } catch (RuntimeException e) {
            throw new FallaDeImpo("formato", url, "La respuesta no es JSON");
        }
        if (!norma.has("tipoNorma") || !norma.has("articulos")) {
            throw new FallaDeImpo("formato", url, "La respuesta no trae una norma");
        }
        return new Leida(norma, "sha256:" + Huellas.sha256(cuerpo));
    }

    private byte[] traer(String url) {
        RuntimeException ultima = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                pausar();
                HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "El Parte (proyecto didáctico; https://github.com/andresdiegolanda/el-parte)")
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (respuesta.statusCode() != 200) {
                    throw new FallaDeImpo(respuesta.statusCode() >= 500 ? "servidor" : "http", url,
                            "IMPO respondió " + respuesta.statusCode());
                }
                if (respuesta.body() == null || respuesta.body().length == 0) {
                    throw new FallaDeImpo("formato", url, "Respuesta vacía");
                }
                return respuesta.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FallaDeImpo("sinRespuesta", url, "Interrumpido");
            } catch (java.io.IOException | RuntimeException e) {
                ultima = e instanceof RuntimeException r ? r : new FallaDeImpo("sinRespuesta", url, e.getClass().getSimpleName());
                if (e instanceof FallaDeImpo falla && "http".equals(falla.tipoDeFalla())) {
                    break;
                }
            }
        }
        if (ultima instanceof FallaDeImpo falla) {
            throw falla;
        }
        throw new FallaDeImpo("sinRespuesta", url, "Sin respuesta después de " + INTENTOS + " intentos");
    }

    /** Espera lo que falte para que pasen {@code pausaMs} desde el pedido anterior, de quien sea. */
    private synchronized void pausar() {
        long espera = ultimoPedido + pausaMs - System.currentTimeMillis();
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ultimoPedido = System.currentTimeMillis();
    }

    /** Una falla de la fuente, con el tipo de {@code fuente.fallida} (docs/contratos.md). */
    public static class FallaDeImpo extends RuntimeException {
        private final String tipoDeFalla;
        private final String url;

        public FallaDeImpo(String tipoDeFalla, String url, String detalle) {
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
