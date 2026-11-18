package io.github.andresdiegolanda.elparte.modelo;

import java.math.BigDecimal;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.presupuesto.AdvisorDePresupuesto;
import io.github.andresdiegolanda.elparte.presupuesto.SinPresupuesto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Las llamadas al modelo de todos los agentes pasan por acá.
 *
 * <ul>
 * <li>Cada agente usa el modelo que le fijó su corrida (ADR 0003), pasado en las opciones de cada
 * pedido.</li>
 * <li>La respuesta se pide como salida estructurada y se convierte al {@code record} del agente. Si el
 * modelo se quedó sin tokens de salida antes de terminar, la falla lo dice, porque lo que llega no es
 * una respuesta mal formada sino una cortada.</li>
 * <li>Cada respuesta se graba, y en modo reproducción se lee de la grabación sin llamar al modelo.</li>
 * <li>Cada llamada pasa por el advisor de presupuesto, que reserva antes y anota después en el libro de
 * la corrida (ADR 0005). Si no alcanza, la llamada no se hace, y también eso se graba.</li>
 * <li>Cada llamada emite {@code uso.modelo}, se haga o no, con los tokens, el costo según la tabla de
 * precios y lo reservado.</li>
 * </ul>
 */
@Service
public class Modelo {

    public static final String HECHA = "hecha";
    public static final String REPRODUCIDA = "reproducida";
    public static final String ERROR = "error";
    public static final String RECHAZADA = "rechazadaPorPresupuesto";
    /** Lo que dice la API cuando la respuesta llegó al máximo de tokens de salida pedido. */
    static final String CORTADA_EN_EL_MAXIMO = "max_tokens";

    private final ChatClient chat;
    private final Grabaciones grabaciones;
    private final Corridas corridas;
    private final Emisor emisor;
    private final JsonMapper json;

    public Modelo(ChatClient.Builder chat, AdvisorDePresupuesto presupuesto, Grabaciones grabaciones, Corridas corridas,
            Emisor emisor, JsonMapper json) {
        this.chat = chat.defaultAdvisors(presupuesto).build();
        this.grabaciones = grabaciones;
        this.corridas = corridas;
        this.emisor = emisor;
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
            if (grabada.has("sinPresupuesto")) {
                String limite = grabada.path("sinPresupuesto").asString();
                emitirUso(pedido, modelo, RECHAZADA, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, limite);
                throw new SinPresupuesto(limite, BigDecimal.ZERO);
            }
            emitirUso(pedido, modelo, REPRODUCIDA, grabada.path("tokensEntrada").asLong(),
                    grabada.path("tokensSalida").asLong(), BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
            if (grabada.has("error")) {
                throw new FallaDelModelo(grabada.path("error").asString());
            }
            return convertir(conversor, grabada.path("texto").asString(), grabada.path("cortada").asBoolean(false),
                    pedido.maximoTokensSalida());
        }

        long inicio = System.currentTimeMillis();
        ChatClientResponse respuesta;
        try {
            respuesta = chat.prompt()
                    .system(pedido.sistema())
                    .user(usuario)
                    .options(AnthropicChatOptions.builder().model(modelo).maxTokens(pedido.maximoTokensSalida()))
                    .advisors(a -> a.param(AdvisorDePresupuesto.CORRIDA, pedido.corridaId())
                            .param(AdvisorDePresupuesto.MODELO, modelo)
                            .param(AdvisorDePresupuesto.PRESUPUESTO, contexto.presupuesto())
                            .param(AdvisorDePresupuesto.MAXIMO_SALIDA, pedido.maximoTokensSalida()))
                    .call()
                    .chatClientResponse();
        } catch (SinPresupuesto sinPresupuesto) {
            // Se graba como una respuesta, así la corrida repetida se corta en el mismo lugar.
            grabaciones.guardar(pedido.corridaId(), clave, "modelo",
                    json.createObjectNode().put("modelo", modelo).put("sinPresupuesto", sinPresupuesto.limite()));
            emitirUso(pedido, modelo, RECHAZADA, 0, 0, BigDecimal.ZERO, sinPresupuesto.reserva(), 0,
                    sinPresupuesto.limite());
            throw sinPresupuesto;
        } catch (RuntimeException e) {
            String detalle = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            grabaciones.guardar(pedido.corridaId(), clave, "modelo",
                    json.createObjectNode().put("modelo", modelo).put("error", detalle));
            emitirUso(pedido, modelo, ERROR, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis() - inicio,
                    null);
            throw new FallaDelModelo(detalle);
        }
        long duracion = System.currentTimeMillis() - inicio;
        String texto = respuesta.chatResponse().getResult().getOutput().getText();
        boolean cortada = cortada(respuesta.chatResponse().getResult().getMetadata().getFinishReason());
        Usage uso = respuesta.chatResponse().getMetadata().getUsage();
        long entrada = valor(uso == null ? null : uso.getPromptTokens());
        long salida = valor(uso == null ? null : uso.getCompletionTokens());

        ObjectNode grabacion = json.createObjectNode();
        grabacion.put("modelo", modelo);
        grabacion.put("texto", texto);
        grabacion.put("tokensEntrada", entrada);
        grabacion.put("tokensSalida", salida);
        if (cortada) {
            grabacion.put("cortada", true);
        }
        grabaciones.guardar(pedido.corridaId(), clave, "modelo", grabacion);

        emitirUso(pedido, modelo, HECHA, entrada, salida, decimal(respuesta, AdvisorDePresupuesto.COSTO),
                decimal(respuesta, AdvisorDePresupuesto.RESERVA), duracion, null);
        return convertir(conversor, texto, cortada, pedido.maximoTokensSalida());
    }

    static boolean cortada(String motivoDeFin) {
        return motivoDeFin != null && CORTADA_EN_EL_MAXIMO.equalsIgnoreCase(motivoDeFin.trim());
    }

    /**
     * La respuesta convertida al {@code record} del agente. Una respuesta cortada que igual se puede
     * convertir vale; si no se puede, la falla dice que se cortó y en cuántos tokens.
     */
    static <T> T convertir(BeanOutputConverter<T> conversor, String texto, boolean cortada, int maximoTokensSalida) {
        try {
            T valor = conversor.convert(texto);
            if (valor == null) {
                throw new FallaDelModelo("La respuesta del modelo vino vacía");
            }
            return valor;
        } catch (FallaDelModelo falla) {
            throw falla;
        } catch (RuntimeException e) {
            if (cortada) {
                throw new FallaDelModelo("La respuesta del modelo se cortó en el máximo de " + maximoTokensSalida
                        + " tokens de salida");
            }
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
            BigDecimal reserva, long duracionMs, String limite) {
        emisor.emitir(pedido.corridaId(), Tipos.USO_MODELO, "presupuesto", pedido.causaId(), pedido.posicion(),
                new UsoModelo(pedido.agente(), modelo, resultado, entrada, salida, 0, 0, costo, reserva, duracionMs,
                        limite));
    }

    private static BigDecimal decimal(ChatClientResponse respuesta, String clave) {
        Object valor = respuesta.context().get(clave);
        return valor instanceof BigDecimal numero ? numero : BigDecimal.ZERO;
    }

    private static long valor(Integer n) {
        return n == null ? 0 : n;
    }
}
