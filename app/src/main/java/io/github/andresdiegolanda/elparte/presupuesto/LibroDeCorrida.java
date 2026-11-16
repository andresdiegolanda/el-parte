package io.github.andresdiegolanda.elparte.presupuesto;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Presupuesto;

/**
 * El libro de la corrida (ADR 0005): una fila por corrida con lo gastado, lo reservado y los pasos.
 *
 * <p>Varios agentes gastan del mismo presupuesto a la vez, así que la reserva es un solo
 * {@code update} que solo pasa si alcanza: PostgreSQL bloquea la fila y vuelve a mirar la condición
 * para cada uno. Cada operación va en su propia transacción, para que una reserva se vea enseguida y
 * no se deshaga si el agente que la pidió falla después.
 */
@Component
public class LibroDeCorrida {

    private final JdbcClient jdbc;
    private final TransactionTemplate aparte;

    public LibroDeCorrida(JdbcClient jdbc, PlatformTransactionManager transacciones) {
        this.jdbc = jdbc;
        this.aparte = new TransactionTemplate(transacciones);
        this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reserva el peor caso de una llamada y suma un paso. Vacío si alcanzó; si no, el límite que no
     * alcanza, que queda en el libro: desde ahí se rechaza todo lo que pida esa corrida.
     */
    public Optional<String> reservar(String corridaId, Presupuesto presupuesto, BigDecimal reserva) {
        return aparte.execute(estado -> {
            int reservadas = jdbc.sql("""
                    update parte.libros_de_corrida l
                    set reservado_usd = l.reservado_usd + :reserva, pasos = l.pasos + 1, actualizado_en = now()
                    where l.corrida_id = :corrida and l.agotado is null
                      and l.gastado_usd + l.reservado_usd + :reserva <= :costo
                      and l.pasos < :pasos
                      and exists (select 1 from parte.corridas c where c.id = l.corrida_id and c.estado = 'en_curso')
                    """)
                    .param("reserva", reserva)
                    .param("corrida", corridaId)
                    .param("costo", presupuesto.costoMaximoUsd())
                    .param("pasos", presupuesto.pasosMaximos())
                    .update();
            if (reservadas == 1) {
                return Optional.<String>empty();
            }
            String limite = jdbc.sql("""
                    select coalesce(l.agotado,
                                    case when c.estado <> 'en_curso' then coalesce(c.limite_alcanzado, 'tiempo')
                                         when l.pasos >= :pasos then 'pasos'
                                         else 'costo' end)
                    from parte.libros_de_corrida l join parte.corridas c on c.id = l.corrida_id
                    where l.corrida_id = :corrida
                    """)
                    .param("pasos", presupuesto.pasosMaximos())
                    .param("corrida", corridaId)
                    .query(String.class).single();
            jdbc.sql("update parte.libros_de_corrida set agotado = :limite where corrida_id = :corrida and agotado is null")
                    .param("limite", limite)
                    .param("corrida", corridaId)
                    .update();
            return Optional.of(jdbc.sql("select agotado from parte.libros_de_corrida where corrida_id = :corrida")
                    .param("corrida", corridaId).query(String.class).single());
        });
    }

    /** Anota lo gastado de verdad y libera la reserva. Una llamada que falló libera sin gastar. */
    public void registrar(String corridaId, BigDecimal reserva, BigDecimal gastado) {
        aparte.executeWithoutResult(estado -> jdbc.sql("""
                update parte.libros_de_corrida
                set gastado_usd = gastado_usd + :gastado, reservado_usd = greatest(reservado_usd - :reserva, 0),
                    actualizado_en = now()
                where corrida_id = :corrida
                """)
                .param("gastado", gastado)
                .param("reserva", reserva)
                .param("corrida", corridaId)
                .update());
    }
}
