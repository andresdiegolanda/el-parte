package io.github.andresdiegolanda.elparte;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Un modelo de mentira para las pruebas: responde como responderían el refutador y el analista, sin red
 * ni costo, y guarda qué modelo le pidieron en cada llamada. Para el refutador, las llamadas y
 * adjudicaciones salen PROVEN y las aclaraciones salen INFERRED; el analista repite la afirmación y
 * agrega por qué importa y qué mirar. Cada respuesta cuenta 1000 tokens de entrada y 200 de salida.
 */
public class ModeloDePrueba implements ChatModel {

    private static final Pattern CANDIDATO = Pattern.compile("Candidato \\((\\w+)\\):\\n(.*)\\n");
    private static final Pattern HECHO = Pattern.compile("Hecho graduado \\((\\w+), (\\w+)\\):\\n(.*)\\n");

    private final List<String> modelosPedidos = new CopyOnWriteArrayList<>();
    private volatile boolean sinCredito;

    @Override
    public ChatResponse call(Prompt pedido) {
        modelosPedidos.add(pedido.getOptions() == null ? "" : String.valueOf(pedido.getOptions().getModel()));
        if (sinCredito) {
            throw new IllegalStateException("400: Your credit balance is too low to access the Anthropic API (prueba)");
        }
        Matcher hecho = HECHO.matcher(pedido.getUserMessage().getText());
        if (hecho.find()) {
            return respuesta("""
                    {"queDice": "%s", "porQueImporta": "Es una compra de una entidad que seguís (%s).",
                     "queMirar": "Si aparece una actualización de esta misma compra."}""".formatted(
                    hecho.group(3).replace("\"", "'"), hecho.group(1)));
        }
        Matcher candidato = CANDIDATO.matcher(pedido.getUserMessage().getText());
        String tipo = candidato.find() ? candidato.group(1) : "";
        String resumen = candidato.group(2).replace("\"", "'");
        String respuesta = "aclaracion".equals(tipo)
                ? """
                  {"grado": "INFERRED", "afirmacion": "%s", "fragmentos": ["tender.id = 1200004"],
                   "razonamiento": "La publicación es una enmienda del llamado 1200004 y su documento describe el cambio.",
                   "busqueda": null, "fuenteDice": "La publicación modifica el llamado 1200004."}""".formatted(resumen)
                : """
                  {"grado": "PROVEN", "afirmacion": "%s", "fragmentos": ["buyer.name = Banco de Previsión Social"],
                   "razonamiento": null, "busqueda": null, "fuenteDice": "La publicación dice lo mismo."}"""
                        .formatted(resumen);
        return respuesta(respuesta);
    }

    private static ChatResponse respuesta(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(1000, 200)).build());
    }

    /** Mientras está prendido, cada llamada falla como falla la API sin crédito. */
    public void sinCredito(boolean sinCredito) {
        this.sinCredito = sinCredito;
    }

    public int llamadas() {
        return modelosPedidos.size();
    }

    public List<String> modelosPedidos() {
        return List.copyOf(modelosPedidos);
    }
}
