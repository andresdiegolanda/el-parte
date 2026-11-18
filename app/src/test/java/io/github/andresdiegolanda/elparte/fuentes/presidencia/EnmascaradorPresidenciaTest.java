package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * El tapado de nombres de Presidencia, con las formas que tienen los títulos y resúmenes reales de la
 * normativa (docs/como-se-hizo/presidencia.md). Los nombres son inventados.
 */
class EnmascaradorPresidenciaTest {

    private static final List<String> BPS = List.of("Banco de Previsión Social", "BPS");

    private static String tapar(String texto) {
        return EnmascaradorPresidencia.tapar(texto, BPS).texto();
    }

    @Test
    void tapaLosNombresDeVariasPalabras() {
        assertThat(tapar("Resolución S/N/026 Se designa en misión oficial a Ana Inventada"))
                .isEqualTo("Resolución S/N/026 Se designa en misión oficial a [persona]");
        assertThat(tapar("Se designa en misión oficial a Ana Inventada, Pedro Ficticio y Luisa de los Supuestos."))
                .isEqualTo("Se designa en misión oficial a [persona], [persona] y [persona].");
        assertThat(tapar("Designación de Ana María Inventada como asesora"))
                .isEqualTo("Designación de [persona] como asesora");
    }

    @Test
    void tapaElTituloOElCargoConElNombre() {
        assertThat(tapar("al señor Presidente de la República Prof. Pedro Ficticio"))
                .isEqualTo("al señor Presidente de la República [persona]");
        assertThat(tapar("al señor Subsecretario Ficticio")).isEqualTo("al señor [persona]");
        assertThat(tapar("con el Dr. Ficticio")).isEqualTo("con el [persona]");
    }

    @Test
    void noTocaLasInstitucionesNiLasEntidadesSeguidas() {
        String texto = "Se autoriza la transferencia al Banco de Previsión Social (BPS) desde el Ministerio de Economía y Finanzas";
        assertThat(tapar(texto)).isEqualTo(texto);
        assertThat(tapar("Se designa Ministro interino de Industria, Energía y Minería"))
                .isEqualTo("Se designa Ministro interino de Industria, Energía y Minería");
    }

    @Test
    void tapaUnNombreCortadoConPuntosSuspensivos() {
        assertThat(tapar("para dar respuesta al pedido formulado por Ana de...")).isEqualTo("para dar respuesta al pedido formulado por [persona]...");
        assertThat(tapar("formulado por Pedro…")).isEqualTo("formulado por [persona]…");
    }

    @Test
    void unApellidoSoloSinTituloNoSeVe() {
        // El límite de tapar por reglas: queda dicho en docs/como-se-hizo/presidencia.md.
        assertThat(tapar("Se acepta la renuncia de Ficticio")).isEqualTo("Se acepta la renuncia de Ficticio");
    }

    @Test
    void cuentaLoQueTapa() {
        assertThat(EnmascaradorPresidencia.tapar("a Ana Inventada y a Pedro Ficticio", BPS).tapados()).isEqualTo(2);
        assertThat(EnmascaradorPresidencia.tapar("", BPS).tapados()).isZero();
    }
}
