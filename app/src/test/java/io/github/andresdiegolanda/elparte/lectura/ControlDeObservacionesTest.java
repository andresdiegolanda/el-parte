package io.github.andresdiegolanda.elparte.lectura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.lectura.ControlDeObservaciones.HechoLeido;

/** El control en código de las observaciones del lector de expedientes (ADR 0018). */
class ControlDeObservacionesTest {

    private static final Map<String, HechoLeido> LEIDOS = Map.of(
            "arce:a:adjudicacion", new HechoLeido("arce:a:adjudicacion", "2026-10-02", "PROVEN",
                    "Se adjudicó a PROVEEDOR S A el mantenimiento por 35000 UYU por mes."),
            "arce:b:adjudicacion", new HechoLeido("arce:b:adjudicacion", "2026-11-04", "PROVEN",
                    "Se adjudicó a PROVEEDOR S A otro mantenimiento por 41000 UYU por mes."),
            "arce:c:llamado", new HechoLeido("arce:c:llamado", "2026-10-20", "INFERRED",
                    "El llamado 1376108 se prorrogó."));

    private static String descarte(String clase, String texto, List<String> hechos) {
        return ControlDeObservaciones.descarte(clase, texto, hechos, LEIDOS);
    }

    @Test
    void pasaUnaObservacionBienCitada() {
        assertThat(descarte("proveedor", "PROVEEDOR S A recibió dos adjudicaciones de mantenimiento, el 2026-10-02 y el 4 de noviembre, por 35000 y 41000 UYU por mes.",
                List.of("arce:a:adjudicacion", "arce:b:adjudicacion"))).isNull();
    }

    @Test
    void laCantidadDeHechosCitadosSePuedeDecir() {
        assertThat(descarte("proveedor", "El mismo proveedor aparece en 2 adjudicaciones.",
                List.of("arce:a:adjudicacion", "arce:b:adjudicacion"))).isNull();
    }

    @Test
    void descartaLoQueNoSePuedeControlar() {
        assertThat(descarte("sospecha", "Algo.", List.of("arce:a:adjudicacion", "arce:b:adjudicacion"))).contains("clase");
        assertThat(descarte("otra", " ", List.of("arce:a:adjudicacion", "arce:b:adjudicacion"))).contains("no dice nada");
        assertThat(descarte("otra", "Se repite.", List.of("arce:a:adjudicacion", "arce:a:adjudicacion"))).contains("menos de dos");
        assertThat(descarte("otra", "Se repite.", List.of("arce:a:adjudicacion", "arce:x:llamado"))).contains("arce:x:llamado");
        assertThat(descarte("proveedor", "Entre las dos suman 76000 UYU por mes.", List.of("arce:a:adjudicacion", "arce:b:adjudicacion")))
                .contains("cifras", "76000");
        assertThat(descarte("proveedor", "Es sospechoso que el mismo proveedor gane dos veces.",
                List.of("arce:a:adjudicacion", "arce:b:adjudicacion"))).contains("califica");
        assertThat(descarte("cadena", "Se prorrogó porque nadie se presentó.", List.of("arce:c:llamado", "arce:a:adjudicacion")))
                .contains("porque");
    }

    @Test
    void elGradoNoSuperaAlDelHechoMasDebil() {
        assertThat(ControlDeObservaciones.gradoMaximo(List.of("arce:a:adjudicacion", "arce:b:adjudicacion"), LEIDOS)).isEqualTo("PROVEN");
        assertThat(ControlDeObservaciones.gradoMaximo(List.of("arce:a:adjudicacion", "arce:c:llamado"), LEIDOS)).isEqualTo("INFERRED");
    }
}
