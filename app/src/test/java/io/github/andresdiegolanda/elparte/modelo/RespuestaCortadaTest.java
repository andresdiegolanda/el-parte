package io.github.andresdiegolanda.elparte.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import io.github.andresdiegolanda.elparte.refutador.Refutador.Veredicto;

/**
 * Una respuesta que llegó al máximo de tokens de salida no es una respuesta mal formada: la falla lo
 * dice, para que el veredicto que queda NOT_MENTIONED explique por qué (T48).
 */
class RespuestaCortadaTest {

    private static final BeanOutputConverter<Veredicto> CONVERSOR = new BeanOutputConverter<>(Veredicto.class);
    private static final String ENTERA = """
            {"grado": "PROVEN", "afirmacion": "El Diario Oficial publicó el Decreto 214/026.",
             "fragmentos": ["nombreNorma = PRORROGA"], "razonamiento": null, "busqueda": null,
             "fuenteDice": "Lo dice el nombre de la norma."}""";
    private static final String CORTADA = "{\"afirmacion\":\"El Diario Oficial del 2026-09-10 publicó el Decreto 214/026\",\"grado\":";

    @Test
    void laApiDiceMaxTokensCuandoSeCorta() {
        assertThat(Modelo.cortada("max_tokens")).isTrue();
        assertThat(Modelo.cortada("MAX_TOKENS")).isTrue();
        assertThat(Modelo.cortada("end_turn")).isFalse();
        assertThat(Modelo.cortada("")).isFalse();
        assertThat(Modelo.cortada(null)).isFalse();
    }

    @Test
    void unaRespuestaCortadaDiceDondeSeCorto() {
        assertThatThrownBy(() -> Modelo.convertir(CONVERSOR, CORTADA, true, 4000))
                .isInstanceOf(Modelo.FallaDelModelo.class)
                .hasMessage("La respuesta del modelo se cortó en el máximo de 4000 tokens de salida");
    }

    @Test
    void unaRespuestaMalFormadaSinCorteSigueSiendoMalFormada() {
        assertThatThrownBy(() -> Modelo.convertir(CONVERSOR, CORTADA, false, 4000))
                .isInstanceOf(Modelo.FallaDelModelo.class)
                .hasMessage("La respuesta del modelo no tiene la forma pedida");
    }

    @Test
    void unaRespuestaQueLlegoAlMaximoPeroEstaEnteraVale() {
        assertThat(Modelo.convertir(CONVERSOR, ENTERA, true, 4000).grado()).isEqualTo("PROVEN");
    }
}
