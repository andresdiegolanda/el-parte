package io.github.andresdiegolanda.elparte.mensajes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura del registro de mensajes que escribe el observador ({@code parte.mensajes}).
 *
 * <p>Un agente que necesita un mensaje anterior de la cadena (por ejemplo, el refutador necesita el
 * candidato) lo pide acá. Como el observador escribe en paralelo, {@link #esperar} reintenta un rato.
 */
@Component
public class RegistroDeMensajes {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(30);
    private static final long PAUSA_MS = 100;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RegistroDeMensajes(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Sobre> buscar(String mensajeId) {
        return jdbc.sql("select * from parte.mensajes where mensaje_id = :id")
                .param("id", mensajeId)
                .query(this::aSobre)
                .optional();
    }

    /** Espera a que el observador haya guardado el mensaje. */
    public Sobre esperar(String mensajeId) {
        long limite = System.nanoTime() + ESPERA_MAXIMA.toNanos();
        while (true) {
            Optional<Sobre> sobre = buscar(mensajeId);
            if (sobre.isPresent()) {
                return sobre.get();
            }
            if (System.nanoTime() > limite) {
                throw new IllegalStateException("El observador no guardó " + mensajeId + " a tiempo");
            }
            try {
                Thread.sleep(PAUSA_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Se interrumpió la espera de " + mensajeId, e);
            }
        }
    }

    /** Los mensajes de una corrida, en el orden en que se emitieron. */
    public List<Sobre> deCorrida(String corridaId) {
        return jdbc.sql("select * from parte.mensajes where corrida_id = :c order by emitido_en, mensaje_id")
                .param("c", corridaId)
                .query(this::aSobre)
                .list();
    }

    public List<Sobre> deCorridaYTipo(String corridaId, String tipo) {
        return jdbc.sql("select * from parte.mensajes where corrida_id = :c and tipo = :t order by emitido_en, mensaje_id")
                .param("c", corridaId)
                .param("t", tipo)
                .query(this::aSobre)
                .list();
    }

    public <T> T contenido(Sobre sobre, Class<T> tipo) {
        return json.treeToValue(sobre.contenido(), tipo);
    }

    private Sobre aSobre(ResultSet fila, int numero) throws SQLException {
        return new Sobre(
                fila.getString("mensaje_id"),
                fila.getString("tipo"),
                fila.getInt("version"),
                fila.getString("corrida_id"),
                fila.getString("causa_id"),
                fila.getString("agente"),
                fila.getTimestamp("emitido_en").toInstant(),
                json.readTree(fila.getString("contenido")));
    }
}
