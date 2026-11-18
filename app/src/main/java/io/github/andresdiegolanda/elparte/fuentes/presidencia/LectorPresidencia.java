package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Lee las resoluciones de la normativa de Presidencia en gub.uy (ADR 0006, docs/fuentes/presidencia.md). No
 * hay API ni RSS: se lee el HTML del listado, filtrado por tipo de norma, año y mes, y la página de cada
 * resolución. El robots.txt de gub.uy no prohíbe ni la normativa ni esos filtros.
 *
 * <p>Es la fuente con el permiso más débil: se lee a ritmo bajo, con {@code elparte.presidencia.pausa-ms}
 * antes de cada pedido, diez segundos por omisión, y se cita siempre. El listado trae diez resoluciones por
 * página, de la más nueva a la más vieja, y unas 440 por mes: para una noche vieja se busca la página por
 * bisección en lugar de recorrerlas todas.
 *
 * <p>Lo que devuelve trae nombres de personas tal como los publica la fuente: el explorador y el refutador
 * los tapan antes de guardar nada (ADR 0008).
 */
@Component
public class LectorPresidencia {

    public static final String FUENTE = "presidencia";
    /** El tipo de norma "Resolución" en el filtro del listado. Las leyes y los decretos se leen en IMPO. */
    static final String RESOLUCIONES = "276";
    private static final int POR_PAGINA = 10;
    private static final int INTENTOS = 3;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern ITEM = Pattern.compile("<li class=\"Media\">(.*?)</li>", Pattern.DOTALL);
    private static final Pattern FECHA_DEL_ITEM = Pattern.compile("Box-info\">\\s*(\\d{2}/\\d{2}/\\d{4})\\s*<");
    private static final Pattern ENLACE = Pattern.compile("<a href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern PARRAFO = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern TOTAL = Pattern.compile("de (\\d+) resultados");
    private static final Pattern NODO = Pattern.compile("\"currentPath\"\\s*:\\s*\"node\\\\?/(\\d+)\"");
    private static final Pattern TITULO = Pattern.compile("<h2 class=\"Page-Title\">(.*?)</h2>", Pattern.DOTALL);
    private static final Pattern FECHA_DE_LA_PAGINA = Pattern.compile("class=\"Page-date\"\\s*>\\s*(\\d{2}/\\d{2}/\\d{4})\\s*<");
    private static final Pattern DESCRIPCION = Pattern.compile("<div class=\"Page-description\">(.*?)</div>", Pattern.DOTALL);
    private static final Pattern PDF = Pattern.compile("<a href=\"(https://medios\\.presidencia\\.gub\\.uy/[^\"]+\\.pdf)\"");

    private final HttpClient http;
    private final String urlBase;
    private final long pausaMs;

    public LectorPresidencia(@Value("${elparte.presidencia.url-base:https://www.gub.uy}") String urlBase,
            @Value("${elparte.presidencia.pausa-ms:10000}") long pausaMs) {
        this.http = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        this.urlBase = urlBase;
        this.pausaMs = pausaMs;
    }

    /** Una resolución del listado. El resumen puede venir cortado con puntos suspensivos. */
    public record Entrada(LocalDate fecha, String titulo, String resumen, String ruta) {
    }

    /** La página de una resolución: su número de nodo, sus textos completos y el PDF. */
    public record Resolucion(String nodo, LocalDate fecha, String titulo, String resumen, String pdf) {
    }

    /** La URL pública de una resolución por su número de nodo, que no lleva nombres de personas. */
    public static String urlPublica(String nodo) {
        return "https://www.gub.uy/presidencia/node/" + nodo;
    }

    /** Las resoluciones de Presidencia de un día, según la fecha del listado. */
    public List<Entrada> resoluciones(LocalDate dia) {
        Map<Integer, Pagina> leidas = new HashMap<>();
        IntFunction<Pagina> pagina = n -> leidas.computeIfAbsent(n, numero -> pagina(dia, numero));
        Pagina primera = pagina.apply(0);
        if (primera.entradas().isEmpty()) {
            return List.of();
        }
        int paginas = Math.max(1, (primera.total() + POR_PAGINA - 1) / POR_PAGINA);
        // La primera página cuya última resolución no es más nueva que el día: desde ahí aparecen las del día.
        int desde = 0;
        if (primera.ultima().isAfter(dia)) {
            int bajo = 1;
            int alto = paginas - 1;
            desde = paginas;
            while (bajo <= alto) {
                int medio = (bajo + alto) >>> 1;
                Pagina p = pagina.apply(medio);
                if (p.entradas().isEmpty() || !p.ultima().isAfter(dia)) {
                    desde = medio;
                    alto = medio - 1;
                } else {
                    bajo = medio + 1;
                }
            }
        }
        List<Entrada> delDia = new ArrayList<>();
        for (int n = desde; n < paginas; n++) {
            Pagina p = pagina.apply(n);
            p.entradas().stream().filter(e -> e.fecha().equals(dia)).forEach(delDia::add);
            if (p.entradas().isEmpty() || p.ultima().isBefore(dia)) {
                break;
            }
        }
        return delDia;
    }

    /** La página de una resolución, por su ruta en el listado. */
    public Resolucion porRuta(String ruta) {
        // La ruta lleva el título, y a veces nombres de personas: en una falla se nombra el listado.
        String paraFallas = urlBase + "/presidencia/institucional/normativa";
        if (!ruta.startsWith("/presidencia/institucional/normativa/")) {
            throw new FallaDePresidencia("formato", paraFallas, "No es una ruta de la normativa de Presidencia");
        }
        return resolucion(urlBase + ruta, paraFallas);
    }

    /** La página de una resolución, por su número de nodo. */
    public Resolucion porNodo(String nodo) {
        if (!nodo.matches("\\d+")) {
            throw new FallaDePresidencia("noEncontrado", urlBase + "/presidencia/node/" + nodo, "No es un número de nodo: " + nodo);
        }
        return resolucion(urlBase + "/presidencia/node/" + nodo, urlPublica(nodo));
    }

    private record Pagina(List<Entrada> entradas, int total) {
        LocalDate ultima() {
            return entradas.getLast().fecha();
        }
    }

    String urlDelListado(LocalDate dia, int pagina) {
        return urlBase + "/presidencia/institucional/normativa?field_tipo_de_norma_target_id=" + RESOLUCIONES + "&year="
                + dia.getYear() + "&month=" + String.format("%02d", dia.getMonthValue()) + "&page=" + pagina;
    }

    private Pagina pagina(LocalDate dia, int numero) {
        String url = urlDelListado(dia, numero);
        String html = traer(url, url);
        List<Entrada> entradas = new ArrayList<>();
        Matcher items = ITEM.matcher(html);
        while (items.find()) {
            String item = items.group(1);
            Matcher fecha = FECHA_DEL_ITEM.matcher(item);
            Matcher enlace = ENLACE.matcher(item);
            if (!fecha.find() || !enlace.find()) {
                throw new FallaDePresidencia("formato", url, "Una resolución del listado no tiene fecha o enlace");
            }
            Matcher parrafo = PARRAFO.matcher(item);
            entradas.add(new Entrada(LocalDate.parse(fecha.group(1), FECHA), texto(enlace.group(2)),
                    parrafo.find() ? texto(parrafo.group(1)) : "", enlace.group(1)));
        }
        Matcher total = TOTAL.matcher(html);
        if (!entradas.isEmpty() && !total.find()) {
            throw new FallaDePresidencia("formato", url, "El listado no dice cuántas resoluciones hay");
        }
        return new Pagina(entradas, entradas.isEmpty() ? 0 : Integer.parseInt(total.group(1)));
    }

    private Resolucion resolucion(String url, String paraFallas) {
        String html = traer(url, paraFallas);
        Matcher nodo = NODO.matcher(html);
        Matcher titulo = TITULO.matcher(html);
        Matcher fecha = FECHA_DE_LA_PAGINA.matcher(html);
        if (!nodo.find() || !titulo.find() || !fecha.find()) {
            throw new FallaDePresidencia("formato", paraFallas, "La página no es la de una resolución");
        }
        Matcher descripcion = DESCRIPCION.matcher(html);
        Matcher pdf = PDF.matcher(html);
        return new Resolucion(nodo.group(1), LocalDate.parse(fecha.group(1), FECHA), texto(titulo.group(1)),
                descripcion.find() ? texto(descripcion.group(1)) : "", pdf.find() ? pdf.group(1) : null);
    }

    /** El texto de un trozo de HTML: sin etiquetas, sin entidades y con los espacios juntos. */
    static String texto(String html) {
        return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " ")).replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private String traer(String url, String paraFallas) {
        RuntimeException ultima = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                if (pausaMs > 0) {
                    Thread.sleep(pausaMs * intento);
                }
                HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "El Parte (proyecto didactico; https://github.com/andresdiegolanda/el-parte)")
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (respuesta.statusCode() == 404) {
                    throw new FallaDePresidencia("noEncontrado", paraFallas, "Presidencia respondió 404");
                }
                if (respuesta.statusCode() != 200) {
                    throw new FallaDePresidencia(respuesta.statusCode() >= 500 || respuesta.statusCode() == 429 ? "servidor" : "http",
                            paraFallas, "Presidencia respondió " + respuesta.statusCode());
                }
                return new String(respuesta.body(), StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FallaDePresidencia("sinRespuesta", paraFallas, "Interrumpido");
            } catch (java.io.IOException | RuntimeException e) {
                ultima = e instanceof RuntimeException r ? r : new FallaDePresidencia("sinRespuesta", paraFallas, e.getClass().getSimpleName());
                if (e instanceof FallaDePresidencia falla
                        && ("http".equals(falla.tipoDeFalla()) || "noEncontrado".equals(falla.tipoDeFalla()))) {
                    break;
                }
            }
        }
        if (ultima instanceof FallaDePresidencia falla) {
            throw falla;
        }
        throw new FallaDePresidencia("sinRespuesta", paraFallas, "Sin respuesta después de " + INTENTOS + " intentos");
    }

    /** Una falla de la fuente, con el tipo de {@code fuente.fallida} (docs/contratos.md). */
    public static class FallaDePresidencia extends RuntimeException {
        private final String tipoDeFalla;
        private final String url;

        public FallaDePresidencia(String tipoDeFalla, String url, String detalle) {
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
