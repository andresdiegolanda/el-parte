package io.github.andresdiegolanda.elparte.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Las llamadas al modelo de todos los agentes pasan por acá.
 *
 * <ul>
 * <li>Cada agente usa el modelo que le fijó su corrida (ADR 0003), pasado en las opciones de cada
 * pedido.</li>
 * <li>La respuesta se pide como salida estructurada y se convierte al {@code record} del agente.</li>
 * <li>Cada respuesta se graba, y en modo reproducción se lee de la grabación sin llamar al modelo.</li>
 * <li>Cada llamada emite {@code uso.modelo} con los tokens y el costo según la tabla de precios. El
 * presupuesto que corta la corrida llega con T22.</li>
 * </ul>
 */
@Service
public class Modelo {

    private static final BigDecimal MILLON = BigDecimal.valueOf(1_000_000);

    private final ChatClient chat;
    private final Grabaciones grabaciones;
    private final Corridas corridas;
    private final Emisor emisor;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public Modelo(ChatClient.Builder chat, Grabaciones grabaciones, Corridas corridas, Emisor emisor, JdbcClient jdbc,
            JsonMapper json) {
        this.chat = chat.build();
        this.grabaciones = grabaciones;
        this.corridas = corridas;
        this.emisor = emisor;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Un pedido al modelo. {@code posicion} ordena el {@code uso.modelo} entre los mensajes de la misma causa. */
    public record Pedido(String corridaId, String agente, String causaId, int posicion, String sistema, String usuario,
            int maximoTokensSalida) {
    }

    public <T> T pedir(Pedido pedido, Class<T> tipo) {
        CorridaIniciada contexto = corridas.contexto(pedido.corridaId());
        String modelo = contexto.modelos().get(pedido.agente());
        if (modelo == null) {
            throw new IllegalStateException("La corrida " + pedido.corridaId() + " no fija modelo para " + pedido.agente());
        }
        BeanOutputConverter<T> conversor = new BeanOutputConverter<>(tipo);
        String usuario = pedido.usuario() + "\n\n" + conversor.getFormat();
        String clave = "modelo:" + pedido.agente() + ":"
                + Huellas.sha256(modelo + "\n" + pedido.sistema() + "\n" + usuario).substring(0, 32);

        if ("reproduccion".equals(contexto.modo())) {
            JsonNode grabada = grabaciones.leer(contexto.reproduceA(), clave)
                    .orElseThrow(() -> new Grabaciones.SinGrabacionException(contexto.reproduceA(), clave));
            emitirUso(pedido, modelo, "reproducida", grabada.path("tokensEntrada").asLong(),
                    grabada.path("tokensSalida").asLong(), BigDecimal.ZERO, 0);
            if (grabada.has("error")) {
                throw new FallaDelModelo(grabada.path("error").asString());
            }
            return convertir(conversor, grabada.path("texto").asString());
        }

        long inicio = System.currentTimeMillis();
        ChatResponse respuesta;
        try {
            respuesta = chat.prompt()
                    .system(pedido.sistema())
                    .user(usuario)
                    .options(AnthropicChatOptions.builder().model(modelo).maxTokens(pedido.maximoTokensSalida()))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            String detalle = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            grabaciones.guardar(pedido.corridaId(), clave, "modelo",
                    json.createObjectNode().put("modelo", modelo).put("error", detalle));
            emitirUso(pedido, modelo, "error", 0, 0, BigDecimal.ZERO, System.currentTimeMillis() - inicio);
            throw new FallaDelModelo(detalle);
        }
        long duracion = System.currentTimeMillis() - inicio;
        String texto = respuesta.getResult().getOutput().getText();
        Usage uso = respuesta.getMetadata().getUsage();
        long entrada = valor(uso == null ? null : uso.getPromptTokens());
        long salida = valor(uso == null ? null : uso.getCompletionTokens());

        ObjectNode grabacion = json.createObjectNode();
        grabacion.put("modelo", modelo);
        grabacion.put("texto", texto);
        grabacion.put("tokensEntrada", entrada);
        grabacion.put("tokensSalida", salida);
        grabaciones.guardar(pedido.corridaId(), clave, "modelo", grabacion);

        emitirUso(pedido, modelo, "hecha", entrada, salida, costo(pedido.corridaId(), modelo, entrada, salida), duracion);
        return convertir(conversor, texto);
    }

    private static <T> T convertir(BeanOutputConverter<T> conversor, String texto) {
        try {
            T valor = conversor.convert(texto);
            if (valor == null) {
                throw new FallaDelModelo("La respuesta del modelo vino vacía");
            }
            return valor;
        } catch (FallaDelModelo falla) {
            throw falla;
        } catch (RuntimeException e) {
            throw new FallaDelModelo("La respuesta del modelo no tiene la forma pedida");
        }
    }

    /**
     * El modelo no respondió, o respondió algo que no tiene la forma pedida. Se graba igual que una
     * respuesta, así la corrida repetida falla en el mismo lugar. Quien llama decide qué hacer: el
     * refutador, por ejemplo, gradúa NOT_MENTIONED, porque sin veredicto no se publica.
     */
    public static class FallaDelModelo extends RuntimeException {
        public FallaDelModelo(String detalle) {
            super(detalle);
        }
    }

    private void emitirUso(Pedido pedido, String modelo, String resultado, long entrada, long salida, BigDecimal costo,
            long duracionMs) {
        emisor.emitir(pedido.corridaId(), Tipos.USO_MODELO, "presupuesto", pedido.causaId(), pedido.posicion(),
                new UsoModelo(pedido.agente(), modelo, resultado, entrada, salida, 0, 0, costo, BigDecimal.ZERO,
                        duracionMs));
    }

    /** El precio que regía al empezar la corrida (ADR 0005). Sin precio, la llamada se rechaza antes. */
    private BigDecimal costo(String corridaId, String modelo, long entrada, long salida) {
        Optional<BigDecimal[]> precio = jdbc.sql("""
                select p.usd_millon_entrada, p.usd_millon_salida
                from parte.precios_de_modelo p, parte.corridas c
                where c.id = :corrida and p.modelo = :modelo and p.desde <= c.iniciada_en
                order by p.desde desc limit 1
                """)
                .param("corrida", corridaId)
                .param("modelo", modelo)
                .query((fila, n) -> new BigDecimal[] { fila.getBigDecimal(1), fila.getBigDecimal(2) })
                .optional();
        if (precio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return precio.get()[0].multiply(BigDecimal.valueOf(entrada))
                .add(precio.get()[1].multiply(BigDecimal.valueOf(salida)))
                .divide(MILLON, 6, RoundingMode.HALF_UP);
    }

    private static long valor(Integer n) {
        return n == null ? 0 : n;
    }
}
