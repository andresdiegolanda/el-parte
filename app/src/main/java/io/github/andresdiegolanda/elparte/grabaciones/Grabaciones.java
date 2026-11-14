package io.github.andresdiegolanda.elparte.grabaciones;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que se leyó de las fuentes y lo que respondió el modelo en cada corrida, ya enmascarado
 * ({@code parte.grabaciones}). Con esto, una corrida se repite en modo reproducción sin red ni costo
 * y produce los mismos mensajes (docs/plan.md, fin de semana 2).
 *
 * <p>En una corrida normal, cada lectura se hace de verdad y se graba. En una de reproducción, se lee
 * de la grabación de la corrida que se repite, y si falta, la corrida falla en lugar de salir a la red.
 */
@Component
public class Grabaciones {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Corridas corridas;

    public Grabaciones(JdbcClient jdbc, JsonMapper json, Corridas corridas) {
        this.jdbc = jdbc;
        this.json = json;
        this.corridas = corridas;
    }

    public JsonNode conGrabacion(String corridaId, String clave, String tipo, Supplier<JsonNode> lecturaReal) {
        CorridaIniciada contexto = corridas.contexto(corridaId);
        if ("reproduccion".equals(contexto.modo())) {
            return leer(contexto.reproduceA(), clave)
                    .orElseThrow(() -> new SinGrabacionException(contexto.reproduceA(), clave));
        }
        JsonNode valor = lecturaReal.get();
        guardar(corridaId, clave, tipo, valor);
        // Se devuelve lo guardado y no lo leído: la base reordena las claves del JSON, y la corrida
        // repetida tiene que ver exactamente lo mismo que la original, hasta en el pedido al modelo.
        return leer(corridaId, clave).orElse(valor);
    }

    public Optional<JsonNode> leer(String corridaId, String clave) {
        return jdbc.sql("select contenido from parte.grabaciones where corrida_id = :c and clave = :k")
                .param("c", corridaId)
                .param("k", clave)
                .query(String.class)
                .optional()
                .map(json::readTree);
    }

    public void guardar(String corridaId, String clave, String tipo, JsonNode contenido) {
        jdbc.sql("""
                insert into parte.grabaciones (corrida_id, clave, tipo, contenido)
                values (:c, :k, :t, cast(:v as jsonb))
                on conflict do nothing
                """)
                .param("c", corridaId)
                .param("k", clave)
                .param("t", tipo)
                .param("v", json.writeValueAsString(contenido))
                .update();
    }

    /** Falta una grabación: la corrida de reproducción no puede seguir sin salir a la red. */
    public static class SinGrabacionException extends RuntimeException {
        public SinGrabacionException(String corrida, String clave) {
            super("La corrida " + corrida + " no tiene grabado " + clave);
        }
    }
}
