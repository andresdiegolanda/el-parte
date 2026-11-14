package io.github.andresdiegolanda.elparte.fuentes.arce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.FallaDeArce;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.ItemDelFeed;
import tools.jackson.databind.json.JsonMapper;

/** La lectura del feed y de las publicaciones, contra un ARCE de mentira. */
class LectorArceTest {

    static ArceDePrueba arce;
    static LectorArce lector;

    @BeforeAll
    static void arrancar() {
        arce = new ArceDePrueba().arrancar();
        lector = new LectorArce(JsonMapper.builder().build(), arce.urlBase());
    }

    @AfterAll
    static void parar() {
        arce.close();
    }

    @Test
    void leeElFeedDelMesConHoraLocal() {
        List<ItemDelFeed> items = lector.feedDelMes(YearMonth.of(2026, 11));

        assertThat(items).hasSize(6);
        assertThat(items.get(2)).isEqualTo(new ItemDelFeed("llamado-1200001", LocalDateTime.of(2026, 11, 12, 10, 15), "llamado"));
        assertThat(lector.urlDelFeed(YearMonth.of(2026, 1))).endsWith("/rss/2026/01");
    }

    @Test
    void leeUnaPublicacionYSuHuella() {
        LectorArce.Leida leida = lector.leer("llamado-1200001");

        assertThat(leida.publicacion().path("id").asString()).isEqualTo("llamado-1200001");
        assertThat(leida.hashContenido()).matches("sha256:[0-9a-f]{64}");
        assertThat(lector.leer("llamado-1200001").hashContenido()).isEqualTo(leida.hashContenido());
    }

    @Test
    void unaRespuestaQueNoEsJsonEsUnaFallaDeFormato() {
        assertThatThrownBy(() -> lector.leer("roto"))
                .isInstanceOf(FallaDeArce.class)
                .satisfies(e -> assertThat(((FallaDeArce) e).tipoDeFalla()).isEqualTo("formato"));
    }

    @Test
    void laUrlPublicaSiempreEsLaDeArce() {
        assertThat(LectorArce.urlPublica("llamado-1200001"))
                .isEqualTo("https://www.comprasestatales.gub.uy/ocds/release/llamado-1200001");
    }
}
