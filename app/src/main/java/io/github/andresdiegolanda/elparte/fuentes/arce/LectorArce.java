package io.github.andresdiegolanda.elparte.fuentes.arce;

import java.io.ByteArrayInputStream;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee la API de ARCE (docs/fuentes/compras-estatales.md): el feed mensual de publicaciones y cada
 * publicación en OCDS. No guarda nada ni tapa nada: eso lo hacen el explorador y el refutador.
 *
 * <p>Las horas que da ARCE vienen sin zona en el feed y con "Z" en el JSON, pero se comportan como
 * hora de Uruguay: casi todo se publica entre las 7 y las 17. Por eso se toman como hora local.
 */
@Component
public class LectorArce {

    private static final DateTimeFormatter FECHA_DEL_FEED =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH);
    private static final int INTENTOS = 3;

    private final RestClient http;
    private final JsonMapper json;
    private final String urlBase;

    public LectorArce(JsonMapper json,
            @Value("${elparte.arce.url-base:https://www.comprasestatales.gub.uy/ocds}") String urlBase) {
        HttpClient cliente = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.json = json;
        this.urlBase = urlBase;
    }

    /** Una entrada del feed: qué publicación, cuándo (hora de Uruguay) y de qué clase. */
    public record ItemDelFeed(String publicacionId, LocalDateTime publicada, String categoria) {
    }

    /** Una publicación leída: el JSON de la publicación (sin tapar) y la huella del contenido completo. */
    public record Leida(JsonNode publicacion, String hashContenido) {
    }

    public String urlDelFeed(YearMonth mes) {
        return urlBase + "/rss/" + mes.getYear() + "/" + String.format("%02d", mes.getMonthValue());
    }

    public String urlDePublicacion(String publicacionId) {
        return urlBase + "/release/" + publicacionId;
    }

    /** La URL pública de una publicación, la que se cita en los hechos. */
    public static String urlPublica(String publicacionId) {
        return "https://www.comprasestatales.gub.uy/ocds/release/" + publicacionId;
    }

    public List<ItemDelFeed> feedDelMes(YearMonth mes) {
        byte[] cuerpo = traer(urlDelFeed(mes));
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document documento = fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(cuerpo));
            NodeList items = documento.getElementsByTagName("item");
            List<ItemDelFeed> resultado = new ArrayList<>(items.getLength());
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                resultado.add(new ItemDelFeed(
                        texto(item, "guid"),
                        LocalDateTime.parse(texto(item, "pubDate"), FECHA_DEL_FEED),
                        texto(item, "category")));
            }
            return resultado;
        } catch (Exception e) {
            throw new FallaDeArce("formato", urlDelFeed(mes), "El feed no se pudo leer como RSS: " + e.getMessage());
        }
    }

    public Leida leer(String publicacionId) {
        byte[] cuerpo = traer(urlDePublicacion(publicacionId));
        JsonNode raiz;
        try {
            raiz = json.readTree(cuerpo);
        } catch (RuntimeException e) {
            throw new FallaDeArce("formato", urlDePublicacion(publicacionId), "La respuesta no es JSON");
        }
        JsonNode publicacion = raiz.path("releases").path(0);
        if (publicacion.isMissingNode()) {
            throw new FallaDeArce("formato", urlDePublicacion(publicacionId), "La respuesta no trae la publicación");
        }
        return new Leida(publicacion, "sha256:" + Huellas.sha256(cuerpo));
    }

    private byte[] traer(String url) {
        RuntimeException ultima = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                byte[] cuerpo = http.get().uri(url).retrieve().body(byte[].class);
                if (cuerpo == null || cuerpo.length == 0) {
                    throw new FallaDeArce("formato", url, "Respuesta vacía");
                }
                return cuerpo;
            } catch (RuntimeException e) {
                ultima = e;
                try {
                    Thread.sleep(1000L * intento);
                } catch (InterruptedException interrumpido) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (ultima instanceof FallaDeArce falla) {
            throw falla;
        }
        throw new FallaDeArce("sinRespuesta", url, "Sin respuesta después de " + INTENTOS + " intentos: "
                + (ultima == null ? "" : ultima.getClass().getSimpleName()));
    }

    private static String texto(Element elemento, String etiqueta) {
        NodeList nodos = elemento.getElementsByTagName(etiqueta);
        return nodos.getLength() == 0 ? "" : nodos.item(0).getTextContent().trim();
    }

    /** Una falla de la fuente, con el tipo de {@code fuente.fallida} (docs/contratos.md). */
    public static class FallaDeArce extends RuntimeException {
        private final String tipoDeFalla;
        private final String url;

        public FallaDeArce(String tipoDeFalla, String url, String detalle) {
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
