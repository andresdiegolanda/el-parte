package io.github.andresdiegolanda.elparte.fuentes.impo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.fuentes.impo.IndiceImpo.Entrada;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Las piezas del explorador de IMPO que no necesitan red ni base: el índice, el tapado y los nombres. */
class PiezasDeImpoTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void delIndiceSalenLasLeyesYLosDecretosConSuIdentificador() {
        List<Entrada> entradas = IndiceImpo.leer(ImpoDePrueba.indice(), 2026, "indice.pdf");
        assertThat(entradas).extracting(Entrada::nombre)
                .containsExactly("Ley 20.999", "Decreto 300/026", "Decreto 301/026");
        assertThat(entradas).extracting(Entrada::identificador)
                .containsExactly("leyes-originales/20999-2026", "decretos/300-2026", "decretos/301-2026");
        assertThat(entradas.get(1).sumario()).isEqualTo("Reglaméntase el régimen de prueba ante el Banco de Previsión Social");

        // Como lo da PDFBox con un índice real: el número de orden, y el sumario en varias líneas hasta la página.
        List<Entrada> deTexto = IndiceImpo.deTexto("""
                Librería IMPO
                7 - Decreto 223/026.- Reglaméntase la Ley 20.504, de fecha 26 de junio
                de 2026, por la que se establece un régimen especial. (4.083*R)................. Pág. 8
                CONSEJO DE SALARIOS
                8 - Consejos de Salarios S/n.- Consejo de Salarios del Grupo 13. (4.103)........ Pág. 9
                1 - Ley 20.531.- Apruébase la Rendición de Cuentas. (4.200)..................... Pág. 3
                """, 2026);
        assertThat(deTexto).extracting(Entrada::identificador).containsExactly("decretos/223-2026", "leyes-originales/20531-2026");
        assertThat(deTexto.get(0).sumario())
                .isEqualTo("Reglaméntase la Ley 20.504, de fecha 26 de junio de 2026, por la que se establece un régimen especial");
    }

    @Test
    void seTapanLosFirmantesYLosNombresConTituloOTratamiento() {
        assertThat(EnmascaradorImpo.tapar("Desígnase a la Sra. Fulana de Tal como responsable.").texto())
                .isEqualTo("Desígnase a la [persona] como responsable.");
        assertThat(EnmascaradorImpo.tapar("Concédese a la señora Fulana de Tal una pensión.").texto())
                .isEqualTo("Concédese a la señora [persona] una pensión.");
        assertThat(EnmascaradorImpo.tapar("Al Dr. Mengano de Tal, Téc. III Médico.").texto()).doesNotContain("Mengano");
        // Un cargo no es un nombre.
        assertThat(EnmascaradorImpo.tapar("Lo firma el señor Ministro de Economía y Finanzas.").texto())
                .isEqualTo("Lo firma el señor Ministro de Economía y Finanzas.");

        JsonNode norma = json.readTree("""
                {"tipoNorma": "Decreto", "nombreNorma": "PRUEBA", "vistos": "VISTO: lo que pidió la Sra. Fulana de Tal;",
                 "articulos": [{"nroArticulo": "1", "textoArticulo": "Desígnase al Esc. Mengano de Tal."}],
                 "firmantes": "MENGANO DE TAL - FULANA DE TAL"}
                """);
        EnmascaradorImpo.Enmascarada tapada = EnmascaradorImpo.enmascarar(norma);
        assertThat(tapada.norma().has("firmantes")).isFalse();
        assertThat(json.writeValueAsString(tapada.norma())).doesNotContain("Fulana", "Mengano", "FULANA", "MENGANO");
        assertThat(tapada.tapados()).isEqualTo(3);
        assertThat(norma.has("firmantes")).as("el original no cambia").isTrue();
    }

    @Test
    void unaEntidadSeReconocePorSuNombreSinTildesYPorSuSiglaEnMayusculas() {
        NombresDeEntidad bps = new NombresDeEntidad("organismo:bps", "Banco de Previsión Social",
                List.of("Banco de Previsión Social", "BPS"));
        assertThat(bps.aparece("ANTE EL BANCO DE PREVISION SOCIAL")).isTrue();
        assertThat(bps.aparece("los aportes al BPS")).isTrue();
        assertThat(bps.aparece("bps")).isFalse();
        assertThat(bps.aparece("Banco de Previsión")).isFalse();

        NombresDeEntidad ute = new NombresDeEntidad("organismo:ute",
                "Administración Nacional de Usinas y Trasmisiones Eléctricas (UTE)", List.of());
        assertThat(ute.aparece("la Administración Nacional de Usinas y Trasmisiones Eléctricas")).isTrue();
        assertThat(ute.aparece("convenio con UTE")).isTrue();
        assertThat(ute.aparece("la UTEC y la Universidad")).isFalse();
    }
}
