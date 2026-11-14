package io.github.andresdiegolanda.elparte.refutador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.fuentes.arce.Enmascarador;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.FallaDeArce;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.Leida;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoCotejado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EvidenciaConUrl;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Prueba;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.modelo.Modelo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El refutador: intenta tirar abajo cada candidato nuevo o actualizado antes de que se publique, y es
 * el único que pone el grado (ADR 0004).
 *
 * <p>Vuelve a leer la fuente por su cuenta, sin confiar en lo que leyó el explorador, y le pide al
 * modelo que compare la afirmación con la publicación. Las pruebas de fecha y de duplicado no
 * necesitan el modelo: salen de la publicación y del cotejo del archivista.
 */
@Component
public class Refutador {

    public static final String AGENTE = "refutador";
    private static final Set<String> GRADOS = Set.of("PROVEN", "INFERRED", "NOT_MENTIONED");
    private static final int MAXIMO_ITEMS = 10;
    private static final int TOKENS_DE_SALIDA = 1200;

    static final String SISTEMA = """
            Sos el refutador de El Parte. Tu trabajo es intentar tirar abajo una afirmación antes de que se \
            publique. Recibís un candidato, que es lo que otro agente dice que pasó, y la publicación original \
            de la fuente, leída de nuevo.

            Decidí el grado de la afirmación:
            - PROVEN: todo lo que afirma está literal en la publicación.
            - INFERRED: no está literal pero se deduce de la publicación. En "razonamiento" explicá de qué se deduce.
            - NOT_MENTIONED: la publicación no respalda la afirmación. En "busqueda" decí qué buscaste y dónde.

            Reglas:
            - Si una parte de la afirmación no está respaldada, acotá la afirmación a lo que sí está y graduá esa \
            versión acotada. La afirmación final va en "afirmacion".
            - Cada fragmento es una copia exacta de un campo de la publicación, con la forma "campo = valor".
            - En "fuenteDice" resumí en una frase qué dice la publicación sobre lo afirmado.
            - No agregues nada que no esté en la publicación.
            - Donde dice [persona] hay un dato personal tapado: no lo reconstruyas ni lo menciones.
            - Escribí en español rioplatense y sin guiones largos.
            """;

    private final RegistroDeMensajes registro;
    private final Corridas corridas;
    private final Grabaciones grabaciones;
    private final LectorArce lector;
    private final Modelo modelo;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JsonMapper json;

    public Refutador(RegistroDeMensajes registro, Corridas corridas, Grabaciones grabaciones, LectorArce lector,
            Modelo modelo, Emisor emisor, Procesados procesados, JsonMapper json) {
        this.registro = registro;
        this.corridas = corridas;
        this.grabaciones = grabaciones;
        this.lector = lector;
        this.modelo = modelo;
        this.emisor = emisor;
        this.procesados = procesados;
        this.json = json;
    }

    /** Lo que devuelve el modelo, como salida estructurada. */
    public record Veredicto(String grado, String afirmacion, List<String> fragmentos, String razonamiento,
            String busqueda, String fuenteDice) {
    }

    @KafkaListener(topics = Tipos.CANDIDATOS_COTEJADOS, groupId = AGENTE)
    @Transactional
    public void recibir(String valor) {
        Sobre sobre = json.readValue(valor, Sobre.class);
        CandidatoCotejado cotejo = json.treeToValue(sobre.contenido(), CandidatoCotejado.class);
        if ("conocido".equals(cotejo.estado()) || !procesados.primeraVez(AGENTE, sobre.mensajeId())) {
            return;
        }
        Candidato candidato = registro.contenido(registro.esperar(sobre.causaId()), Candidato.class);
        CorridaIniciada corrida = corridas.contexto(sobre.corridaId());

        Prueba fecha = pruebaDeFecha(candidato, corrida);
        Prueba duplicado = "nuevo".equals(cotejo.estado())
                ? new Prueba("duplicado", "pasa", "El archivista no encontró otro hecho con esa clave.")
                : new Prueba("duplicado", "pasa", "Es una actualización del hecho " + cotejo.hechoRelacionadoId()
                        + ": " + String.join(" ", cotejo.diferencias()));

        JsonNode releida = releer(sobre.corridaId(), candidato.identificadorEnFuente());
        CandidatoGraduado graduado;
        if (releida.has("falla")) {
            graduado = new CandidatoGraduado(candidato.candidatoId(), cotejo.claveDeHecho(), candidato.resumen(),
                    "NOT_MENTIONED", List.of(), null,
                    "No se pudo volver a leer " + candidato.url() + " (" + releida.path("detalle").asString() + ").",
                    false, List.of(fecha, duplicado,
                            new Prueba("fuenteDice", "falla", "La fuente no respondió al releerla.")));
        } else {
            Veredicto veredicto;
            try {
                veredicto = modelo.pedir(new Modelo.Pedido(sobre.corridaId(), AGENTE, sobre.mensajeId(), 1,
                        SISTEMA, pedidoAlModelo(candidato, corrida, releida.path("publicacion")), TOKENS_DE_SALIDA),
                        Veredicto.class);
            } catch (Modelo.FallaDelModelo falla) {
                veredicto = new Veredicto("NOT_MENTIONED", candidato.resumen(), List.of(), null,
                        "No hubo veredicto del modelo (" + falla.getMessage() + "); sin veredicto no se publica.", null);
            }
            graduado = graduar(candidato, cotejo, veredicto, fecha, duplicado);
        }
        emisor.emitir(sobre.corridaId(), Tipos.CANDIDATOS_GRADUADOS, AGENTE, sobre.mensajeId(), 0, graduado);
    }

    private CandidatoGraduado graduar(Candidato candidato, CandidatoCotejado cotejo, Veredicto veredicto, Prueba fecha,
            Prueba duplicado) {
        String grado = veredicto.grado() == null ? "" : veredicto.grado().trim().toUpperCase();
        String afirmacion = vacio(veredicto.afirmacion()) ? candidato.resumen() : veredicto.afirmacion().trim();
        String razonamiento = vacio(veredicto.razonamiento()) ? null : veredicto.razonamiento().trim();
        String busqueda = vacio(veredicto.busqueda()) ? null : veredicto.busqueda().trim();
        if (!GRADOS.contains(grado)) {
            grado = "NOT_MENTIONED";
            busqueda = "El modelo devolvió un grado que no existe (" + veredicto.grado() + "); no se publica.";
        }
        if ("INFERRED".equals(grado) && razonamiento == null) {
            grado = "NOT_MENTIONED";
            busqueda = "El modelo dijo INFERRED sin decir de qué se deduce; sin razonamiento no se publica (ADR 0004).";
        }
        if ("NOT_MENTIONED".equals(grado) && busqueda == null) {
            busqueda = "Se buscó lo afirmado en la publicación " + candidato.url() + " y no está.";
        }
        if (!"INFERRED".equals(grado)) {
            razonamiento = null;
        }
        List<EvidenciaConUrl> evidencia = new ArrayList<>();
        if (veredicto.fragmentos() != null) {
            veredicto.fragmentos().stream().filter(f -> !vacio(f))
                    .forEach(f -> evidencia.add(new EvidenciaConUrl(candidato.url(), f.trim())));
        }
        String resultado = switch (grado) {
            case "PROVEN" -> "pasa";
            case "INFERRED" -> "deducida";
            default -> "falla";
        };
        Prueba fuenteDice = new Prueba("fuenteDice", resultado,
                vacio(veredicto.fuenteDice()) ? "Sin detalle." : veredicto.fuenteDice().trim());
        return new CandidatoGraduado(candidato.candidatoId(), cotejo.claveDeHecho(), afirmacion, grado, evidencia,
                razonamiento, "NOT_MENTIONED".equals(grado) ? busqueda : null, "pasa".equals(fecha.resultado()),
                List.of(fecha, duplicado, fuenteDice));
    }

    /** Vuelve a leer la publicación, por su cuenta. Se graba como todo lo que se lee. */
    private JsonNode releer(String corridaId, String publicacionId) {
        return grabaciones.conGrabacion(corridaId, "refutador:arce:publicacion:" + publicacionId, "fuente", () -> {
            try {
                Leida leida = lector.leer(publicacionId);
                ObjectNode nueva = json.createObjectNode();
                nueva.set("publicacion", Enmascarador.enmascarar(leida.publicacion()).publicacion());
                nueva.put("hashContenido", leida.hashContenido());
                return nueva;
            } catch (FallaDeArce falla) {
                return json.createObjectNode().put("falla", falla.tipoDeFalla()).put("detalle", falla.getMessage());
            }
        });
    }

    private static Prueba pruebaDeFecha(Candidato candidato, CorridaIniciada corrida) {
        String dia = candidato.fechaPublicacion().length() >= 10 ? candidato.fechaPublicacion().substring(0, 10) : "";
        if (dia.equals(corrida.fechaObjetivo())) {
            return new Prueba("fecha", "pasa", "La publicación es del " + dia
                    + " (hora de Uruguay), la noche leída.");
        }
        if (!dia.isEmpty() && LocalDate.parse(dia).isBefore(LocalDate.parse(corrida.fechaObjetivo()))) {
            return new Prueba("fecha", "no_confirmada", "La publicación es del " + dia
                    + ", anterior a la noche leída: se levantó en los días de margen, por si ARCE la publicó con atraso.");
        }
        return new Prueba("fecha", "no_confirmada", "No se pudo ubicar la publicación en la noche leída.");
    }

    private String pedidoAlModelo(Candidato candidato, CorridaIniciada corrida, JsonNode publicacion) {
        return "Noche leída: " + corrida.fechaObjetivo() + "\n\n"
                + "Candidato (" + candidato.tipoDeHecho() + "):\n" + candidato.resumen() + "\n\n"
                + "Campos que citó el explorador:\n" + candidato.evidencia().fragmento() + "\n\n"
                + "Publicación de la fuente, en OCDS (" + candidato.url() + "):\n"
                + json.writeValueAsString(recortar(publicacion));
    }

    /** Las listas de ítems se cortan para que el pedido no crezca sin límite. */
    private static JsonNode recortar(JsonNode publicacion) {
        JsonNode copia = publicacion.deepCopy();
        recortarItems(copia);
        return copia;
    }

    private static void recortarItems(JsonNode nodo) {
        if (nodo instanceof ObjectNode objeto) {
            objeto.properties().forEach(campo -> {
                if ("items".equals(campo.getKey()) && campo.getValue() instanceof ArrayNode lista) {
                    while (lista.size() > MAXIMO_ITEMS) {
                        lista.remove(lista.size() - 1);
                    }
                }
                recortarItems(campo.getValue());
            });
        } else if (nodo instanceof ArrayNode lista) {
            lista.forEach(Refutador::recortarItems);
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
