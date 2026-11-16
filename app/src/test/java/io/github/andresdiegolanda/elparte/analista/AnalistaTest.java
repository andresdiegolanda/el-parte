package io.github.andresdiegolanda.elparte.analista;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.analista.Analista.Analisis;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EvidenciaConUrl;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;

/** El control en código de lo que escribe el modelo para el analista (ADR 0015). */
class AnalistaTest {

    private static final String URL = "https://www.comprasestatales.gub.uy/ocds/release/adjudicacion-1200002";
    private static final CandidatoGraduado GRADUADO = new CandidatoGraduado("k-arce-0002",
            "arce:ocds-yfs5dr-1200002:adjudicacion",
            "Banco de Previsión Social adjudicó la compra 1200002 a Laboratorio Uno S.A. por 1.250.000 pesos uruguayos.",
            "PROVEN", List.of(new EvidenciaConUrl(URL, "awards[0].value.amount = 1250000")), null, null, true, List.of());
    private static final List<String> FECHAS = List.of("2026-11-06T10:15:00-03:00", "2026-11-06");

    @Test
    void unAnalisisSinDatosInventadosSaleTalCualYConElGradoDelRefutador() {
        HechoAnalizado hecho = Analista.revisar(GRADUADO, FECHAS, new Analisis(
                "El BPS le compró a Laboratorio Uno S.A. por 1250000 pesos; se publicó el 6 de noviembre de 2026.",
                "Fija quién presta el servicio.", "Si aparece una ampliación."));

        assertThat(hecho.grado()).isEqualTo("PROVEN");
        assertThat(hecho.queDice()).startsWith("El BPS le compró");
        assertThat(hecho.porQueImporta()).isEqualTo("Fija quién presta el servicio.");
        assertThat(hecho.queMirar()).isEqualTo("Si aparece una ampliación.");
    }

    @Test
    void unaCifraQueNoEstaEnLaEvidenciaNoSeUsa() {
        HechoAnalizado hecho = Analista.revisar(GRADUADO, FECHAS, new Analisis(
                "El BPS le compró a Laboratorio Uno S.A. por 1.300.000 pesos.",
                "Fija quién presta el servicio.", "Si se amplía dentro de 90 días."));

        assertThat(hecho.queDice()).isEqualTo(GRADUADO.afirmacion());
        assertThat(hecho.porQueImporta()).isEqualTo("Fija quién presta el servicio.");
        assertThat(hecho.queMirar()).isEqualTo("Sin análisis: el modelo citó una cifra que no está en la evidencia.");
    }

    @Test
    void sinRespuestaDelModeloElHechoSaleIgualYDiceQueNoHayAnalisis() {
        HechoAnalizado hecho = Analista.revisar(GRADUADO, FECHAS, null);

        assertThat(hecho.grado()).isEqualTo("PROVEN");
        assertThat(hecho.queDice()).isEqualTo(GRADUADO.afirmacion());
        assertThat(hecho.porQueImporta()).isEqualTo("Sin análisis: el modelo no respondió.");
        assertThat(hecho.queMirar()).startsWith(Analista.SIN_ANALISIS);
    }

    @Test
    void unCampoVacioNoSeUsa() {
        HechoAnalizado hecho = Analista.revisar(GRADUADO, FECHAS, new Analisis(" ", null, "Si aparece una ampliación."));

        assertThat(hecho.queDice()).isEqualTo(GRADUADO.afirmacion());
        assertThat(hecho.porQueImporta()).isEqualTo("Sin análisis: el modelo no lo escribió.");
        assertThat(hecho.queMirar()).isEqualTo("Si aparece una ampliación.");
    }

    @Test
    void lasCifrasSeComparanSinSeparadoresNiCerosALaIzquierda() {
        assertThat(Analista.cifras("Compra Directa por Excepción 04/2026 por 1.250.000,50"))
                .containsExactlyInAnyOrder("42026", "125000050");
        assertThat(Analista.cifrasPermitidas("2026-11-06")).contains("20261106", "2026", "11", "6");
    }
}
