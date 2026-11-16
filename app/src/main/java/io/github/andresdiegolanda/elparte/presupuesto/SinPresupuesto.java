package io.github.andresdiegolanda.elparte.presupuesto;

import java.math.BigDecimal;

/**
 * La llamada al modelo no se hizo: no alcanzaba el presupuesto de la corrida (ADR 0005). No es una
 * falla del modelo. Quien llama decide qué hacer: el refutador deja el candidato sin procesar, y el
 * analista publica el hecho sin análisis.
 */
public class SinPresupuesto extends RuntimeException {

    private final String limite;
    private final BigDecimal reserva;

    public SinPresupuesto(String limite, BigDecimal reserva) {
        super("Se alcanzó el límite de " + limite + " de la corrida: la llamada al modelo no se hizo.");
        this.limite = limite;
        this.reserva = reserva;
    }

    /** {@code costo}, {@code pasos} o {@code tiempo}. */
    public String limite() {
        return limite;
    }

    public BigDecimal reserva() {
        return reserva;
    }
}
