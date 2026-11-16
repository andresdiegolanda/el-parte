package io.github.andresdiegolanda.elparte.acceso;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.BuzonDePrueba;
import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.Navegador;

/** El login por enlace de correo, los invitados y lo que puede hacer cada uno (ADR 0013, T25). */
class LoginTest extends ConServicios {

    @Autowired
    BuzonDePrueba buzon;
    @Autowired
    JdbcClient parte;
    @Value("${local.server.port}")
    int puerto;

    /** Deja una copia de la página en target/paginas/, para mirarla. */
    private static String guardar(String nombre, String html) {
        try {
            Path carpeta = Path.of("target", "paginas");
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve(nombre + ".html"), html);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return html;
    }

    @Test
    void sinEntrarSeVeLaDemostracionYLaSalaPeroNoSeCorreNiSeRevisa() {
        Navegador anonimo = new Navegador(puerto);

        assertThat(anonimo.get("/manana").body()).contains("Cuenta de demostración", "Entrar");
        guardar("entrar", anonimo.get("/entrar").body());
        HttpResponse<String> sala = anonimo.get("/corridas");
        assertThat(sala.statusCode()).isEqualTo(200);
        assertThat(sala.body()).contains("Correr o repetir una corrida es del responsable").doesNotContain(">Correr</button>");
        assertThat(anonimo.get("/revision").statusCode()).isEqualTo(302);
        assertThat(anonimo.post("/entrar", "/corridas", Map.of("modo", "normal", "fecha", "2026-11-12")).headers()
                .firstValue("Location").orElse("")).endsWith("/entrar");
    }

    @Test
    void losInvitadosSeDanDeAltaConElMismoIdentificadorEnLosDosEsquemas() {
        // El alias está en parte.clientes; el correo, solo en cuentas.usuarios, que el rol de la aplicación no ve.
        assertThat(parte.sql("select count(*) from parte.clientes where alias in ('Responsable', 'Invitada uno') and not es_demo")
                .query(Long.class).single()).isEqualTo(2);
        // Un invitado nuevo empieza siguiendo lo mismo que la cuenta de demostración.
        assertThat(parte.sql("""
                select count(*) from parte.suscripciones s join parte.clientes c on c.cliente_id = s.cliente_id
                where c.alias = 'Invitada uno' and s.entidad_id = 'organismo:bps'
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unCorreoNoInvitadoVeLoMismoPeroNoLeLlegaNada() {
        Navegador extrano = new Navegador(puerto);
        int cartasAntes = buzon.cartas().size();

        HttpResponse<String> respuesta = extrano.post("/entrar", "/ott/generate", Map.of("username", "nadie@prueba.test"));

        assertThat(respuesta.statusCode()).isEqualTo(302);
        assertThat(respuesta.headers().firstValue("Location").orElse("")).endsWith("/entrar/enviado");
        assertThat(buzon.cartas()).hasSize(cartasAntes);
    }

    @Test
    void unaInvitadaEntraConSuEnlaceYQuedaRecordadaPeroNoCorreNiRevisa() {
        Navegador navegador = new Navegador(puerto);

        // Pide el enlace con el correo escrito distinto: se compara sin mayúsculas.
        navegador.post("/entrar", "/ott/generate", Map.of("username", " invitada.uno@prueba.TEST "));
        String enlace = buzon.ultimoEnlacePara(INVITADA).orElseThrow();
        assertThat(enlace).startsWith("http://localhost:8080/login/ott?token=");
        String token = enlace.substring(enlace.indexOf("token=") + 6);

        // Abrir el enlace no gasta el token: muestra un botón.
        assertThat(navegador.get("/login/ott?token=" + token).body()).contains("Entrar a El Parte", token);
        HttpResponse<String> entrada = navegador.post("/login/ott?token=" + token, "/login/ott", Map.of("token", token));
        assertThat(entrada.statusCode()).isEqualTo(302);
        assertThat(entrada.headers().firstValue("Location").orElse("")).endsWith("/manana");
        assertThat(guardar("manana-invitada", navegador.get("/manana").body()))
                .contains("El parte de Invitada uno", "Salir").doesNotContain(">Revisión<");

        // El token sirve una sola vez.
        Navegador otro = new Navegador(puerto);
        assertThat(otro.post("/login/ott?token=" + token, "/login/ott", Map.of("token", token)).headers()
                .firstValue("Location").orElse("")).endsWith("/entrar?vencido");

        // No corre ni revisa.
        assertThat(navegador.post("/manana", "/corridas", Map.of("modo", "normal", "fecha", "2026-11-12")).statusCode())
                .isEqualTo(403);
        assertThat(navegador.get("/revision").statusCode()).isEqualTo(403);

        // Queda recordada: con solo la cookie del recordatorio, otro navegador entra como ella.
        HttpCookie recordatorio = navegador.cookies().getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("remember-me")).findFirst().orElseThrow();
        Navegador mananaSiguiente = new Navegador(puerto);
        mananaSiguiente.cookies().getCookieStore().add(null, recordatorio);
        assertThat(mananaSiguiente.get("/manana").body()).contains("El parte de Invitada uno");

        // Al salir se borra el recordatorio.
        navegador.post("/manana", "/logout", Map.of());
        Navegador despues = new Navegador(puerto);
        despues.cookies().getCookieStore().add(null, recordatorio);
        assertThat(despues.get("/manana").body()).contains("Cuenta de demostración");
    }

    @Test
    void elResponsableRevisaYVeLosFormulariosDeLaSala() {
        Navegador responsable = new Navegador(puerto);
        responsable.entrarComo(RESPONSABLE, buzon);

        assertThat(responsable.get("/revision").statusCode()).isEqualTo(200);
        assertThat(responsable.get("/corridas").body()).contains(">Correr</button>", ">Repetir</button>");
        assertThat(responsable.get("/manana").body()).contains(">Revisión<", "El parte de Responsable");
    }
}
