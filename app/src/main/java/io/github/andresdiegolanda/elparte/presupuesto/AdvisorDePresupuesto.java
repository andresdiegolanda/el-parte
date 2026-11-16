package io.github.andresdiegolanda.elparte.presupuesto;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Presupuesto;

/**
 * El advisor de presupuesto (ADR 0005): envuelve cada llamada al modelo del {@code ChatClient}.
 *
 * <ol>
 * <li>Antes de llamar, reserva en el libro de la corrida el peor caso: la entrada estimada más el
 * máximo de salida pedido, por su precio. Si no alcanza, la llamada no se hace.</li>
 * <li>Después, anota lo gastado de verdad y libera la reserva.</li>
 * </ol>
 *
 * <p>Lo que necesita de cada llamada viaja en el contexto del pedido; lo pone el servicio del modelo.
 */
@Component
public class AdvisorDePresupuesto implements CallAdvisor {

    public static final String CORRIDA = "elparte.corrida";
    public static final String PRESUPUESTO = "elparte.presupuesto";
    public static final String MODELO = "elparte.modelo";
    public static final String MAXIMO_SALIDA = "elparte.maximoSalida";
    /** Lo que el advisor deja en el contexto de la respuesta. */
    public static final String RESERVA = "elparte.reservaUsd";
    public static final String COSTO = "elparte.costoUsd";

    /**
     * Para estimar la entrada sin pedirle al proveedor que cuente: en español un token anda por los 4
     * caracteres, y con 3 la estimación queda del lado de reservar de más. [INFERRED]
     */
    static final int CARACTERES_POR_TOKEN = 3;

    private final Precios precios;
    private final LibroDeCorrida libro;

    public AdvisorDePresupuesto(Precios precios, LibroDeCorrida libro) {
        this.precios = precios;
        this.libro = libro;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest pedido, CallAdvisorChain cadena) {
        Map<String, Object> contexto = pedido.context();
        String corrida = (String) contexto.get(CORRIDA);
        String modelo = (String) contexto.get(MODELO);
        Presupuesto presupuesto = (Presupuesto) contexto.get(PRESUPUESTO);
        Integer maximoSalida = (Integer) contexto.get(MAXIMO_SALIDA);
        if (corrida == null || modelo == null || presupuesto == null || maximoSalida == null) {
            throw new IllegalStateException("Una llamada al modelo sin corrida, modelo, presupuesto o máximo de salida: "
                    + "no se puede controlar su gasto");
        }
        Precios.Precio precio = precios.vigente(corrida, modelo).orElseThrow(() -> new IllegalStateException(
                "El modelo " + modelo + " no tiene precio en parte.precios_de_modelo: la llamada no se hace (ADR 0005)"));
        BigDecimal reserva = precio.costo(entradaEstimada(pedido.prompt()), maximoSalida);
        libro.reservar(corrida, presupuesto, reserva).ifPresent(limite -> {
            throw new SinPresupuesto(limite, reserva);
        });

        ChatClientResponse respuesta;
        try {
            respuesta = cadena.nextCall(pedido);
        } catch (RuntimeException e) {
            libro.registrar(corrida, reserva, BigDecimal.ZERO);
            throw e;
        }
        Usage uso = respuesta.chatResponse() == null ? null : respuesta.chatResponse().getMetadata().getUsage();
        BigDecimal costo = precio.costo(valor(uso == null ? null : uso.getPromptTokens()),
                valor(uso == null ? null : uso.getCompletionTokens()));
        libro.registrar(corrida, reserva, costo);
        return respuesta.mutate().context(RESERVA, reserva).context(COSTO, costo).build();
    }

    static long entradaEstimada(Prompt prompt) {
        long caracteres = 0;
        for (Message mensaje : prompt.getInstructions()) {
            caracteres += mensaje.getText() == null ? 0 : mensaje.getText().length();
        }
        return (caracteres + CARACTERES_POR_TOKEN - 1) / CARACTERES_POR_TOKEN;
    }

    private static long valor(Integer n) {
        return n == null ? 0 : n;
    }

    @Override
    public String getName() {
        return "presupuesto";
    }

    /** Primero de todos: ningún otro advisor llega al modelo sin pasar por el presupuesto. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
