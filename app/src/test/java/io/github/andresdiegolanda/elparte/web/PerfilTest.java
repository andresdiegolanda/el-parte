package io.github.andresdiegolanda.elparte.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.BuzonDePrueba;
import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.Navegador;
import io.github.andresdiegolanda.elparte.acceso.AvisoDiario;

/** El perfil: el catálogo, seguir y dejar de seguir, y el aviso diario (ADR 0013, T34). */
class PerfilTest extends ConServicios {

    @Autowired
    BuzonDePrueba buzon;
    @Autowired
    JdbcClient parte;
    @Autowired
    AvisoDiario aviso;
    @Autowired
    Seguimientos seguimientos;
    @Value("${local.server.port}")
    int puerto;

    private List<String> sigue(String alias) {
        return parte.sql("""
                select s.entidad_id from parte.suscripciones s join parte.clientes c on c.cliente_id = s.cliente_id
                where c.alias = :alias order by s.entidad_id
                """).param("alias", alias).query(String.class).list();
    }

    private UUID cliente(String alias) {
        return parte.sql("select cliente_id from parte.clientes where alias = :alias").param("alias", alias)
                .query(UUID.class).single();
    }

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
    void elCatalogoTraeLosOrganismosConSuCodigoDeComprador() {
        assertThat(parte.sql("select count(*) from parte.entidades where identificadores -> 'arce' is not null")
                .query(Long.class).single()).isEqualTo(13);
        assertThat(parte.sql("select identificadores ->> 'arce' from parte.entidades where id = 'organismo:ose'")
                .query(String.class).single()).isEqualTo("[\"66-1\"]");
    }

    @Test
    void sinEntrarSeVeElPerfilDeLaDemostracionSinBotones() {
        Navegador anonimo = new Navegador(puerto);

        HttpResponse<String> perfil = anonimo.get("/perfil");
        assertThat(perfil.statusCode()).isEqualTo(200);
        assertThat(perfil.body())
                .contains("Cuenta de demostración", "Administración de las Obras Sanitarias del Estado (OSE)", "Sigue a 1.")
                .doesNotContain(">Seguir</button>", ">Dejar de seguir</button>", "Aviso diario");

        // Cambiarlo pide entrar, y la demostración sigue igual.
        HttpResponse<String> intento = anonimo.post("/entrar", "/perfil/seguir", Map.of("entidad", "organismo:ose"));
        assertThat(intento.headers().firstValue("Location").orElse("")).endsWith("/entrar");
        assertThat(sigue("Demostración")).containsExactly("organismo:bps");
    }

    @Test
    void unaInvitadaSigueDejaDeSeguirYApagaElAviso() {
        Navegador navegador = new Navegador(puerto);
        navegador.entrarComo(INVITADA, buzon);
        UUID invitada = cliente("Invitada uno");
        try {
            assertThat(navegador.get("/perfil").body())
                    .contains("El perfil de <span>Invitada uno</span>", ">Seguir</button>", ">Dejar de seguir</button>",
                            "No quiero el aviso");

            HttpResponse<String> seguir = navegador.post("/perfil", "/perfil/seguir", Map.of("entidad", "organismo:ose"));
            assertThat(seguir.headers().firstValue("Location").orElse("")).endsWith("/perfil");
            assertThat(sigue("Invitada uno")).containsExactly("organismo:bps", "organismo:ose");

            // Lo que no está en el catálogo no se puede seguir.
            navegador.post("/perfil", "/perfil/seguir", Map.of("entidad", "organismo:inventado"));
            assertThat(sigue("Invitada uno")).containsExactly("organismo:bps", "organismo:ose");

            navegador.post("/perfil", "/perfil/dejar", Map.of("entidad", "organismo:bps"));
            assertThat(sigue("Invitada uno")).containsExactly("organismo:ose");

            assertThat(aviso.quiere(invitada)).isTrue();
            navegador.post("/perfil", "/perfil/aviso", Map.of("quiere", "false"));
            assertThat(aviso.quiere(invitada)).isFalse();
            assertThat(guardar("perfil-invitada", navegador.get("/perfil").body())).contains("Quiero el aviso", "Sigue a 1.");

            // La demostración no cambió.
            assertThat(sigue("Demostración")).containsExactly("organismo:bps");
        } finally {
            // Las otras pruebas cuentan con que la invitada sigue al BPS, como al darse de alta.
            seguimientos.seguir(invitada, "organismo:bps");
            seguimientos.dejar(invitada, "organismo:ose");
            aviso.cambiar(invitada, true);
        }
    }
}
