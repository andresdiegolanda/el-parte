package io.github.andresdiegolanda.elparte.infraestructura;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Registro de lo que procesó cada agente (docs/contratos.md, "Idempotencia", regla 2). Se llama
 * dentro de la misma transacción que los efectos del agente: si el mensaje ya se había procesado,
 * el agente lo descarta.
 */
@Component
public class Procesados {

    private final JdbcClient jdbc;

    public Procesados(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Verdadero la primera vez que este consumidor ve este mensaje. */
    public boolean primeraVez(String consumidor, String mensajeId) {
        return jdbc.sql("""
                insert into parte.mensajes_procesados (consumidor, mensaje_id) values (:consumidor, :mensaje)
                on conflict do nothing
                """)
                .param("consumidor", consumidor)
                .param("mensaje", mensajeId)
                .update() == 1;
    }

    /** Para trabajos largos que se marcan al terminar, como la exploración de una fuente. */
    public boolean yaProcesado(String consumidor, String mensajeId) {
        return jdbc.sql("select count(*) from parte.mensajes_procesados where consumidor = :consumidor and mensaje_id = :mensaje")
                .param("consumidor", consumidor)
                .param("mensaje", mensajeId)
                .query(Integer.class)
                .single() > 0;
    }
}
