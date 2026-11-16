package io.github.andresdiegolanda.elparte.analista;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoCotejado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EvidenciaConUrl;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.modelo.Modelo;
import tools.jackson.databind.json.JsonMapper;

/**
 * El analista: escribe, para quien sigue a las entidades de un hecho, qué dice, por qué importa y qué
 * mirar (ADR 0015). El grado lo puso el refutador y se copia tal cual: el analista no lo puede cambiar
 * (ADR 0004).
 *
 * <p>Lo que escribe el modelo pasa por un control en código antes de salir: una cifra que no está en
 * la afirmación ni en la evidencia es un dato inventado, y ese campo no se usa. Sin análisis, el hecho
 * se publica igual: el análisis ayuda a leer, pero lo que se publica es lo que graduó el refutador.
 */
@Component
public class Analista {

    public static final String AGENTE = "analista";
    /** Con esto empiezan {@code porQueImporta} y {@code queMirar} cuando no hay análisis (docs/contratos.md). */
    public static final String SIN_ANALISIS = "Sin análisis";
    private static final int TOKENS_DE_SALIDA = 600;
    private static final Pattern CIFRA = Pattern.compile("\\d(?:[\\d.,/-]*\\d)?");

    static final String SISTEMA = """
            Sos el analista de El Parte. Recibís un hecho que el refutador ya graduó, con la evidencia que lo \
            respalda, y escribís tres cosas para alguien que sigue a las entidades del hecho y no conoce el \
            tema por dentro:
            - "queDice": la afirmación en lenguaje llano, en una o dos frases.
            - "porQueImporta": por qué le importa a quien sigue a esas entidades, en una o dos frases.
            - "queMirar": qué conviene seguir de cerca después, en una frase.

            Reglas:
            - El grado ya está puesto y no lo cambiás. Si es INFERRED, no lo presentes como seguro: decí que \
            se deduce y de qué.
            - En "queDice" no agregues nada que no esté en la afirmación o en la evidencia.
            - No inventes datos: ningún monto, fecha, número, nombre ni plazo que no esté en lo que recibís. \
            Lo que es interpretación tiene que leerse como interpretación.
            - No opines sobre si la compra está bien o mal, ni sobre personas.
            - Donde dice [persona] hay un dato personal tapado: no lo reconstruyas ni lo menciones.
            - Escribí en español rioplatense y sin guiones largos.
            """;

    private final RegistroDeMensajes registro;
    private final Corridas corridas;
    private final Modelo modelo;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JsonMapper json;

    public Analista(RegistroDeMensajes registro, Corridas corridas, Modelo modelo, Emisor emisor, Procesados procesados,
            JsonMapper json) {
        this.registro = registro;
        this.corridas = corridas;
        this.modelo = modelo;
        this.emisor = emisor;
        this.procesados = procesados;
        this.json = json;
    }

    /** Lo que devuelve el modelo, como salida estructurada. */
    public record Analisis(String queDice, String porQueImporta, String queMirar) {
    }

    @KafkaListener(topics = Tipos.CANDIDATOS_GRADUADOS, groupId = AGENTE)
    @Transactional
    public void recibir(String valor) {
        Sobre sobre = json.readValue(valor, Sobre.class);
        CandidatoGraduado graduado = json.treeToValue(sobre.contenido(), CandidatoGraduado.class);
        if ("NOT_MENTIONED".equals(graduado.grado()) || !procesados.primeraVez(AGENTE, sobre.mensajeId())) {
            return;
        }
        Sobre sobreDelCotejo = registro.esperar(sobre.causaId());
        CandidatoCotejado cotejo = registro.contenido(sobreDelCotejo, CandidatoCotejado.class);
        Candidato candidato = registro.contenido(registro.esperar(sobreDelCotejo.causaId()), Candidato.class);
        CorridaIniciada corrida = corridas.contexto(sobre.corridaId());

        Analisis analisis;
        try {
            analisis = modelo.pedir(new Modelo.Pedido(sobre.corridaId(), AGENTE, sobre.mensajeId(), 1, SISTEMA,
                    pedidoAlModelo(graduado, cotejo, candidato, corrida), TOKENS_DE_SALIDA), Analisis.class);
        } catch (Modelo.FallaDelModelo falla) {
            analisis = null;
        }
        emisor.emitir(sobre.corridaId(), Tipos.HECHOS_ANALIZADOS, AGENTE, sobre.mensajeId(), 0,
                revisar(graduado, List.of(candidato.fechaPublicacion(), corrida.fechaObjetivo()), analisis));
    }

    /**
     * El control en código de lo que escribió el modelo. El grado se copia del refutador. Un campo
     * vacío, o con una cifra que no está en la afirmación ni en la evidencia, no se usa: {@code queDice}
     * vuelve a ser la afirmación, y los otros dos dicen que no hay análisis y por qué. Las fechas de la
     * publicación y de la noche también valen: el modelo las recibe y puede nombrarlas.
     */
    static HechoAnalizado revisar(CandidatoGraduado graduado, List<String> fechas, Analisis analisis) {
        if (analisis == null) {
            String motivo = SIN_ANALISIS + ": el modelo no respondió.";
            return new HechoAnalizado(graduado.candidatoId(), graduado.claveDeHecho(), graduado.grado(),
                    graduado.afirmacion(), motivo, motivo);
        }
        Set<String> cifrasDeLaFuente = cifrasPermitidas(graduado.afirmacion() + "\n" + graduado.evidencia().stream()
                .map(EvidenciaConUrl::fragmento).collect(Collectors.joining("\n"))
                + (graduado.razonamiento() == null ? "" : "\n" + graduado.razonamiento())
                + "\n" + String.join("\n", fechas.stream().filter(f -> f != null).toList()));
        String queDice = usable(analisis.queDice(), cifrasDeLaFuente) == null ? analisis.queDice().trim()
                : graduado.afirmacion();
        return new HechoAnalizado(graduado.candidatoId(), graduado.claveDeHecho(), graduado.grado(), queDice,
                campo(analisis.porQueImporta(), cifrasDeLaFuente), campo(analisis.queMirar(), cifrasDeLaFuente));
    }

    private static String campo(String texto, Set<String> cifrasDeLaFuente) {
        String motivo = usable(texto, cifrasDeLaFuente);
        return motivo == null ? texto.trim() : SIN_ANALISIS + ": " + motivo;
    }

    /** Nulo si el texto se puede usar; si no, por qué no. */
    private static String usable(String texto, Set<String> cifrasDeLaFuente) {
        if (texto == null || texto.isBlank()) {
            return "el modelo no lo escribió.";
        }
        Set<String> inventadas = new HashSet<>(cifras(texto));
        inventadas.removeAll(cifrasDeLaFuente);
        return inventadas.isEmpty() ? null : "el modelo citó una cifra que no está en la evidencia.";
    }

    /**
     * Las cifras de un texto, sin separadores ni ceros a la izquierda, para que "1.200.000" sea lo mismo
     * que "1200000" y "04/2026" lo mismo que "4/2026".
     */
    static Set<String> cifras(String texto) {
        Set<String> cifras = new HashSet<>();
        Matcher m = CIFRA.matcher(texto);
        while (m.find()) {
            cifras.add(sinCeros(m.group().replaceAll("[.,/-]", "")));
        }
        return cifras;
    }

    /**
     * Las cifras que el analista puede nombrar: las del texto de la fuente, enteras y también por partes,
     * para que de "2026-11-06" se pueda decir "6 de noviembre de 2026".
     */
    static Set<String> cifrasPermitidas(String fuente) {
        Set<String> permitidas = cifras(fuente);
        Matcher m = CIFRA.matcher(fuente);
        while (m.find()) {
            for (String parte : m.group().split("[.,/-]")) {
                if (!parte.isEmpty()) {
                    permitidas.add(sinCeros(parte));
                }
            }
        }
        return permitidas;
    }

    private static String sinCeros(String cifra) {
        return cifra.replaceFirst("^0+(?=\\d)", "");
    }

    private String pedidoAlModelo(CandidatoGraduado graduado, CandidatoCotejado cotejo, Candidato candidato,
            CorridaIniciada corrida) {
        List<String> entidades = candidato.entidades().stream().map(e -> e.nombre() + " (" + e.rol() + ")").toList();
        StringBuilder pedido = new StringBuilder()
                .append("Hecho graduado (").append(candidato.tipoDeHecho()).append(", ").append(graduado.grado())
                .append("):\n").append(graduado.afirmacion()).append("\n\n")
                .append("Noche leída: ").append(corrida.fechaObjetivo()).append(". Publicado en ")
                .append(candidato.fuente()).append(" el ").append(candidato.fechaPublicacion())
                .append(graduado.fechaConfirmada() ? "." : ", con atraso: no es de la noche leída.").append("\n\n")
                .append("Entidades del hecho: ").append(String.join("; ", entidades)).append("\n\n");
        if ("actualizacion".equals(cotejo.estado())) {
            pedido.append("Es una actualización de un hecho ya publicado. Lo que cambió: ")
                    .append(String.join(" ", cotejo.diferencias())).append("\n\n");
        }
        if (graduado.razonamiento() != null) {
            pedido.append("De qué se deduce, según el refutador: ").append(graduado.razonamiento()).append("\n\n");
        }
        pedido.append("Evidencia, campos copiados de la publicación:\n");
        graduado.evidencia().forEach(e -> pedido.append("- ").append(e.fragmento()).append("\n"));
        return pedido.toString();
    }
}
