package io.github.andresdiegolanda.elparte;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Un navegador de prueba: guarda las cookies como un navegador, no sigue las redirecciones para poder
 * controlarlas, y manda los formularios con el token CSRF que Spring Security pone en cada formulario.
 */
public class Navegador {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient cliente;

    public Navegador(int puerto) {
        this.base = "http://127.0.0.1:" + puerto;
        this.cliente = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public CookieManager cookies() {
        return cookies;
    }

    public HttpResponse<String> get(String ruta) {
        return enviar(HttpRequest.newBuilder(URI.create(ruta.startsWith("http") ? ruta : base + ruta)).GET().build());
    }

    /** Manda un formulario con el token CSRF que tiene la página {@code desde}. */
    public HttpResponse<String> post(String desde, String ruta, Map<String, String> campos) {
        Matcher m = CSRF.matcher(get(desde).body());
        Map<String, String> todos = new LinkedHashMap<>(campos);
        if (m.find()) {
            todos.put("_csrf", m.group(1));
        }
        return postSinToken(ruta, todos);
    }

    public HttpResponse<String> postSinToken(String ruta, Map<String, String> campos) {
        String cuerpo = campos.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return enviar(HttpRequest.newBuilder(URI.create(base + ruta))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build());
    }

    /** Entra como un invitado: pide el enlace, lo saca del buzón de prueba y lo confirma. */
    public void entrarComo(String correo, BuzonDePrueba buzon) {
        post("/entrar", "/ott/generate", Map.of("username", correo));
        String enlace = buzon.ultimoEnlacePara(correo).orElseThrow(() -> new IllegalStateException("No llegó el enlace"));
        String token = enlace.substring(enlace.indexOf("token=") + "token=".length());
        HttpResponse<String> respuesta = post("/login/ott?token=" + token, "/login/ott", Map.of("token", token));
        if (respuesta.statusCode() != 302 || !respuesta.headers().firstValue("Location").orElse("").endsWith("/manana")) {
            throw new IllegalStateException("No se pudo entrar: " + respuesta.statusCode() + " " + respuesta.headers().map());
        }
    }

    private HttpResponse<String> enviar(HttpRequest pedido) {
        try {
            return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
