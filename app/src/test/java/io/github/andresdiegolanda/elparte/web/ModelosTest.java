package io.github.andresdiegolanda.elparte.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.BuzonDePrueba;
import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.Navegador;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;

/** El modelo de cada agente y sus precios, desde la sala de máquinas (ADR 0003, ADR 0005, T29). */
class ModelosTest extends ConServicios {

    @Autowired
    BuzonDePrueba buzon;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    InicioDeCorrida inicio;
    @Value("${local.server.port}")
    int puerto;

    @Test
    void sinSerResponsableSeVePeroNoSeCambia() {
        Navegador anonimo = new Navegador(puerto);
        String pagina = anonimo.get("/modelos").body();
        assertThat(pagina).contains("refutador", "claude-sonnet-5-5", "Cambiar un modelo o un precio es del responsable")
                .doesNotContain(">Cambiar</button>", ">Cargar</button>");
        assertThat(anonimo.get("/corridas").body()).contains("href=\"/modelos\"");
        // La página no tiene formularios sin sesión: el token sale de la de entrar.
        assertThat(anonimo.post("/entrar", "/modelos/agente", Map.of("agente", "refutador", "modelo", "claude-haiku-5-5"))
                .headers().firstValue("Location").orElse("")).endsWith("/entrar");

        Navegador invitada = new Navegador(puerto);
        invitada.entrarComo(INVITADA, buzon);
        assertThat(invitada.post("/perfil", "/modelos/agente", Map.of("agente", "refutador", "modelo", "claude-haiku-5-5"))
                .statusCode()).isEqualTo(403);
        assertThat(vigente("refutador")).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void elResponsableCambiaElModeloDeUnAgenteYValeDesdeLaCorridaSiguiente() {
        Navegador responsable = new Navegador(puerto);
        responsable.entrarComo(RESPONSABLE, buzon);
        try {
            // Un modelo sin precio no se puede elegir.
            responsable.post("/modelos", "/modelos/agente", Map.of("agente", "refutador", "modelo", "claude-opus-5-5"));
            assertThat(responsable.get("/modelos").body()).contains("no tiene precio");
            assertThat(vigente("refutador")).isEqualTo("claude-sonnet-5-5");

            // Un precio mal cargado no entra.
            responsable.post("/modelos", "/modelos/precio", precio("claude-opus-5-5", "0", "http://precios.test"));
            assertThat(responsable.get("/modelos").body()).contains("no pueden ser gratis");

            // Con su precio, sí; y la corrida siguiente lo usa.
            responsable.post("/modelos", "/modelos/precio", precio("claude-opus-5-5", "4", "https://precios.test/modelos"));
            responsable.post("/modelos", "/modelos/agente", Map.of("agente", "refutador", "modelo", "claude-opus-5-5"));
            guardar("modelos-responsable", responsable.get("/modelos").body());
            assertThat(vigente("refutador")).isEqualTo("claude-opus-5-5");
            String corrida = inicio.iniciarNormal(LocalDate.parse("2026-08-25"));
            assertThat(jdbc.sql("select modelos->>'refutador' from parte.corridas where id = :id").param("id", corrida)
                    .query(String.class).single()).isEqualTo("claude-opus-5-5");
            esperarFin(corrida);
            assertThat(responsable.get("/corridas/" + corrida).body()).contains("refutador: claude-opus-5-5");
        } finally {
            jdbc.sql("insert into parte.modelos_de_agente (agente, modelo) values ('refutador', 'claude-sonnet-5-5')").update();
        }
        assertThat(vigente("refutador")).isEqualTo("claude-sonnet-5-5");
    }

    private static Map<String, String> precio(String modelo, String entrada, String fuente) {
        return Map.of("modelo", modelo, "entrada", entrada, "salida", "20", "cacheLectura", "0,4",
                "cacheEscritura", "5", "fuente", fuente, "consultadoEl", "2026-11-17");
    }

    private String vigente(String agente) {
        return jdbc.sql("select modelo from parte.modelos_de_agente where agente = :a order by desde desc limit 1")
                .param("a", agente).query(String.class).single();
    }

    private void esperarFin(String corridaId) {
        Instant limite = Instant.now().plus(Duration.ofSeconds(120));
        while (Instant.now().isBefore(limite) && "en_curso".equals(jdbc.sql("select estado from parte.corridas where id = :id")
                .param("id", corridaId).query(String.class).single())) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Deja una copia de la página en target/paginas/, para mirarla. */
    private static void guardar(String nombre, String html) {
        try {
            Path carpeta = Path.of("target", "paginas");
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve(nombre + ".html"), html);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
