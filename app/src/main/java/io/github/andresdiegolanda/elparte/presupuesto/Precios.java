package io.github.andresdiegolanda.elparte.presupuesto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** La tabla de precios del modelo (ADR 0005): el precio que regía al empezar cada corrida. */
@Component
public class Precios {

    private static final BigDecimal MILLON = BigDecimal.valueOf(1_000_000);

    private final JdbcClient jdbc;

    public Precios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Dólares por millón de tokens de entrada y de salida. */
    public record Precio(BigDecimal entrada, BigDecimal salida) {

        public BigDecimal costo(long tokensEntrada, long tokensSalida) {
            return entrada.multiply(BigDecimal.valueOf(tokensEntrada))
                    .add(salida.multiply(BigDecimal.valueOf(tokensSalida)))
                    .divide(MILLON, 6, RoundingMode.HALF_UP);
        }
    }

    /** Un cambio de precio es una fila nueva, así cada corrida usa el que regía cuando empezó. */
    public Optional<Precio> vigente(String corridaId, String modelo) {
        return jdbc.sql("""
                select p.usd_millon_entrada, p.usd_millon_salida
                from parte.precios_de_modelo p, parte.corridas c
                where c.id = :corrida and p.modelo = :modelo and p.desde <= c.iniciada_en
                order by p.desde desc limit 1
                """)
                .param("corrida", corridaId)
                .param("modelo", modelo)
                .query((fila, n) -> new Precio(fila.getBigDecimal(1), fila.getBigDecimal(2)))
                .optional();
    }
}
