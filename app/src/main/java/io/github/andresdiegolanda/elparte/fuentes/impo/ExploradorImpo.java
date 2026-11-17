package io.github.andresdiegolanda.elparte.fuentes.impo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.fuentes.impo.IndiceImpo.Entrada;
import io.github.andresdiegolanda.elparte.fuentes.impo.LectorImpo.FallaDeImpo;
import io.github.andresdiegolanda.elparte.fuentes.impo.LectorImpo.Leida;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ExploracionTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.FuenteFallida;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El explorador de IMPO (ADR 0010): lee la edición del Diario Oficial de la noche objetivo y emite un
 * candidato por cada ley o decreto que nombra a una entidad que alguien sigue.
 *
 * <ol>
 * <li>Lee el índice de la edición, en PDF, y se queda con las leyes y los decretos. Si ese día no hubo
 * edición, no hay nada que leer.</li>
 * <li>Lee el JSON de cada una, a diez segundos entre pedidos, y le tapa los datos personales antes de
 * mirarla: sin firmantes, y sin los nombres que siguen a un título o a un tratamiento.</li>
 * <li>Busca en el nombre, los vistos y los artículos a cada entidad seguida. Si no nombra a ninguna, la
 * norma no sigue: el filtro se hace antes de cualquier llamada al modelo.</li>
 * </ol>
 *
 * <p>No lee los avisos ni las resoluciones de los organismos, que son casi todas designaciones de
 * personas. Lo que lee se graba ya tapado, para poder repetir la corrida sin red: del índice se graba la
 * lista de leyes y decretos, no el PDF.
 */
@Component
public class ExploradorImpo {

    public static final String AGENTE = "explorador-impo";
    private static final Logger log = LoggerFactory.getLogger(ExploradorImpo.class);
    private static final int POSICION_DE_FALLAS = 100_000;

    private final LectorImpo lector;
    private final Grabaciones grabaciones;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ExploradorImpo(LectorImpo lector, Grabaciones grabaciones, Emisor emisor, Procesados procesados, JdbcClient jdbc,
            JsonMapper json) {
        this.lector = lector;
        this.grabaciones = grabaciones;
        this.emisor = emisor;
        this.procesados = procesados;
        this.jdbc = jdbc;
        this.json = json;
    }

    // Diez segundos entre pedidos: una edición con muchas leyes y decretos puede pasar los cinco minutos que
    // Kafka espera por omisión entre dos lecturas, y no tiene que darla por caída.
    @KafkaListener(topics = Tipos.CORRIDA_INICIADA, groupId = AGENTE, properties = "max.poll.interval.ms=1800000")
    public void recibir(String valor) {
        Sobre inicio = json.readValue(valor, Sobre.class);
        CorridaIniciada corrida = json.treeToValue(inicio.contenido(), CorridaIniciada.class);
        if (!corrida.fuentes().contains(LectorImpo.FUENTE) || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        explorar(inicio, corrida);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void explorar(Sobre inicio, CorridaIniciada corrida) {
        String corridaId = inicio.corridaId();
        LocalDate fecha = LocalDate.parse(corrida.fechaObjetivo());
        List<NombresDeEntidad> seguidas = entidades(corrida.entidadesSeguidas());

        JsonNode indice = grabaciones.conGrabacion(corridaId, "impo:indice:" + fecha, "fuente", () -> {
            try {
                Optional<byte[]> pdf = lector.indice(fecha);
                ObjectNode grabado = json.createObjectNode().put("edicion", pdf.isPresent());
                ArrayNode entradas = grabado.putArray("entradas");
                pdf.ifPresent(bytes -> IndiceImpo.leer(bytes, fecha.getYear(), lector.urlDelIndice(fecha))
                        .forEach(e -> entradas.add(json.createObjectNode().put("orden", e.orden()).put("tipo", e.tipo())
                                .put("numero", e.numero()).put("anio", e.anio())
                                .put("sumario", EnmascaradorImpo.tapar(e.sumario()).texto()))));
                return grabado;
            } catch (FallaDeImpo falla) {
                return comoGrabacion(falla);
            }
        });
        if (indice.has("falla")) {
            fallo(inicio, 0, indice);
            terminar(inicio, 0, 0, 0);
            return;
        }

        List<Candidato> candidatos = new ArrayList<>();
        List<JsonNode> fallas = new ArrayList<>();
        int leidas = 0;
        for (JsonNode e : indice.path("entradas")) {
            Entrada entrada = new Entrada(e.path("orden").asInt(), e.path("tipo").asString(), e.path("numero").asString(),
                    e.path("anio").asInt(), e.path("sumario").asString());
            JsonNode guardada = grabaciones.conGrabacion(corridaId, "impo:norma:" + entrada.identificador(), "fuente", () -> {
                try {
                    Leida leida = lector.norma(entrada.identificador());
                    EnmascaradorImpo.Enmascarada tapada = EnmascaradorImpo.enmascarar(leida.norma());
                    ObjectNode nueva = json.createObjectNode();
                    nueva.set("norma", tapada.norma());
                    nueva.put("tapados", tapada.tapados());
                    nueva.put("hashContenido", leida.hashContenido());
                    return nueva;
                } catch (FallaDeImpo falla) {
                    return comoGrabacion(falla);
                }
            });
            if (guardada.has("falla")) {
                fallas.add(guardada);
                continue;
            }
            leidas++;
            JsonNode norma = guardada.path("norma");
            List<NombresDeEntidad> nombradas = seguidas.stream().filter(entidad -> nombra(norma, entidad)).toList();
            if (!nombradas.isEmpty()) {
                candidatos.add(CandidatosDeImpo.armar(String.format("k-impo-%04d", candidatos.size() + 1), entrada, norma,
                        nombradas, guardada.path("tapados").asInt(), guardada.path("hashContenido").asString(), fecha));
            }
        }
        for (int i = 0; i < candidatos.size(); i++) {
            emisor.emitir(corridaId, Tipos.CANDIDATOS, AGENTE, inicio.mensajeId(), i + 1, candidatos.get(i));
        }
        for (int i = 0; i < fallas.size(); i++) {
            fallo(inicio, i + 1, fallas.get(i));
        }
        log.info("Exploración de IMPO de {}: {} leyes y decretos leídos, {} candidatos, {} sin procesar", corridaId,
                leidas, candidatos.size(), fallas.size());
        terminar(inicio, leidas, candidatos.size(), fallas.size());
    }

    /** Si la norma nombra a la entidad en su nombre, sus vistos o alguno de sus artículos. */
    static boolean nombra(JsonNode norma, NombresDeEntidad entidad) {
        if (entidad.aparece(norma.path("nombreNorma").asString("")) || entidad.aparece(norma.path("vistos").asString(""))) {
            return true;
        }
        for (JsonNode articulo : norma.path("articulos")) {
            if (entidad.aparece(articulo.path("textoArticulo").asString(""))) {
                return true;
            }
        }
        return false;
    }

    /** Las entidades seguidas, con sus nombres para buscarlas en las normas. */
    private List<NombresDeEntidad> entidades(List<String> ids) {
        List<NombresDeEntidad> entidades = new ArrayList<>();
        for (String id : ids) {
            jdbc.sql("select nombre, identificadores from parte.entidades where id = :id").param("id", id)
                    .query((f, n) -> {
                        List<String> paraImpo = new ArrayList<>();
                        json.readTree(f.getString(2)).path("impo").forEach(nombre -> paraImpo.add(nombre.asString()));
                        return new NombresDeEntidad(id, f.getString(1), paraImpo);
                    })
                    .optional().ifPresent(entidades::add);
        }
        return entidades;
    }

    private JsonNode comoGrabacion(FallaDeImpo falla) {
        return json.createObjectNode().put("falla", falla.tipoDeFalla()).put("url", falla.url())
                .put("detalle", falla.getMessage());
    }

    private void fallo(Sobre inicio, int numero, JsonNode falla) {
        emisor.emitir(inicio.corridaId(), Tipos.FUENTE_FALLIDA, AGENTE, inicio.mensajeId(), POSICION_DE_FALLAS + numero,
                new FuenteFallida(LectorImpo.FUENTE, falla.path("url").asString(), falla.path("falla").asString(),
                        falla.path("detalle").asString()));
    }

    private void terminar(Sobre inicio, int leidas, int emitidos, int sinProcesar) {
        emisor.emitir(inicio.corridaId(), Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                new ExploracionTerminada(LectorImpo.FUENTE, leidas, emitidos, sinProcesar));
    }
}
