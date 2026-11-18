package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.fuentes.NombresDeEntidad;
import io.github.andresdiegolanda.elparte.fuentes.presidencia.LectorPresidencia.Entrada;
import io.github.andresdiegolanda.elparte.fuentes.presidencia.LectorPresidencia.FallaDePresidencia;
import io.github.andresdiegolanda.elparte.fuentes.presidencia.LectorPresidencia.Resolucion;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Evidencia;
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
 * El explorador de Presidencia (ADR 0006): las resoluciones de la normativa de Presidencia de la noche
 * objetivo, y un candidato por cada una que nombra a una entidad seguida. Las leyes y los decretos no se
 * leen acá: llegan por IMPO, que es la publicación oficial.
 *
 * <p>Del listado solo se usa la lista: para cada resolución que nombra a una entidad se lee su página, que
 * trae el número de nodo y los textos completos, y lo que se guarda es eso, con los nombres de personas
 * tapados. Las resoluciones que no nombran a ninguna entidad no se guardan, y la ruta del listado, que a
 * veces lleva nombres, tampoco (ADR 0008). La fecha es la del listado: una resolución que se sube días
 * después de su fecha no se ve la noche de esa fecha (D1).
 */
@Component
public class ExploradorPresidencia {

    public static final String AGENTE = "explorador-presidencia";
    private static final Logger log = LoggerFactory.getLogger(ExploradorPresidencia.class);
    private static final int POSICION_DE_FALLAS = 100_000;
    private static final int CONTEXTO = 300;

    private final LectorPresidencia lector;
    private final Grabaciones grabaciones;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ExploradorPresidencia(LectorPresidencia lector, Grabaciones grabaciones, Emisor emisor, Procesados procesados,
            JdbcClient jdbc, JsonMapper json) {
        this.lector = lector;
        this.grabaciones = grabaciones;
        this.emisor = emisor;
        this.procesados = procesados;
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = Tipos.CORRIDA_INICIADA, groupId = AGENTE)
    public void recibir(String valor) {
        Sobre inicio = json.readValue(valor, Sobre.class);
        CorridaIniciada corrida = json.treeToValue(inicio.contenido(), CorridaIniciada.class);
        if (!corrida.fuentes().contains(LectorPresidencia.FUENTE) || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        explorar(inicio, corrida);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void explorar(Sobre inicio, CorridaIniciada corrida) {
        String corridaId = inicio.corridaId();
        LocalDate fecha = LocalDate.parse(corrida.fechaObjetivo());
        List<NombresDeEntidad> seguidas = entidades(corrida.entidadesSeguidas());
        JsonNode leido = grabaciones.conGrabacion(corridaId, "presidencia:resoluciones:" + fecha, "fuente", () -> {
            try {
                return leer(fecha, seguidas);
            } catch (FallaDePresidencia falla) {
                return json.createObjectNode().put("falla", falla.tipoDeFalla()).put("url", falla.url())
                        .put("detalle", falla.getMessage());
            }
        });
        if (leido.has("falla")) {
            emisor.emitir(corridaId, Tipos.FUENTE_FALLIDA, AGENTE, inicio.mensajeId(), POSICION_DE_FALLAS,
                    new FuenteFallida(LectorPresidencia.FUENTE, leido.path("url").asString(), leido.path("falla").asString(),
                            leido.path("detalle").asString()));
            terminar(inicio, 0, 0);
            return;
        }
        List<Candidato> candidatos = new ArrayList<>();
        for (JsonNode resolucion : leido.path("resoluciones")) {
            List<NombresDeEntidad> nombradas = seguidas.stream()
                    .filter(e -> e.aparece(resolucion.path("titulo").asString("") + "\n" + resolucion.path("resumen").asString("")))
                    .toList();
            if (!nombradas.isEmpty()) {
                candidatos.add(armar(String.format("k-presidencia-%04d", candidatos.size() + 1), resolucion, nombradas));
            }
        }
        for (int i = 0; i < candidatos.size(); i++) {
            emisor.emitir(corridaId, Tipos.CANDIDATOS, AGENTE, inicio.mensajeId(), i + 1, candidatos.get(i));
        }
        int leidas = leido.path("leidas").asInt(0);
        log.info("Exploración de Presidencia de {}: {} resoluciones, {} candidatos", corridaId, leidas, candidatos.size());
        terminar(inicio, leidas, candidatos.size());
    }

    /** Lo que se guarda de una noche: cuántas resoluciones hubo y, tapadas, las que nombran a una entidad. */
    private ObjectNode leer(LocalDate fecha, List<NombresDeEntidad> seguidas) {
        List<Entrada> entradas = lector.resoluciones(fecha);
        List<String> palabras = palabrasDeEntidades(seguidas);
        ArrayNode resoluciones = json.createArrayNode();
        for (Entrada entrada : entradas) {
            String delListado = entrada.titulo() + "\n" + entrada.resumen();
            if (seguidas.stream().noneMatch(e -> e.aparece(delListado))) {
                continue;
            }
            Resolucion pagina = lector.porRuta(entrada.ruta());
            if (seguidas.stream().noneMatch(e -> e.aparece(pagina.titulo() + "\n" + pagina.resumen()))) {
                continue;
            }
            resoluciones.add(tapada(pagina, palabras, json));
        }
        ObjectNode leido = json.createObjectNode();
        leido.put("leidas", entradas.size());
        leido.set("resoluciones", resoluciones);
        return leido;
    }

    /** Una resolución con los nombres de personas tapados y la huella de lo tapado. */
    public static ObjectNode tapada(Resolucion pagina, List<String> palabrasDeEntidades, JsonMapper json) {
        ObjectNode resolucion = json.createObjectNode();
        resolucion.put("nodo", pagina.nodo());
        resolucion.put("fecha", pagina.fecha().toString());
        resolucion.put("titulo", EnmascaradorPresidencia.tapar(pagina.titulo(), palabrasDeEntidades).texto());
        resolucion.put("resumen", EnmascaradorPresidencia.tapar(pagina.resumen(), palabrasDeEntidades).texto());
        if (pagina.pdf() != null) {
            resolucion.put("pdf", pagina.pdf());
        }
        resolucion.put("hashContenido", "sha256:" + Huellas.sha256(json.writeValueAsBytes(resolucion)));
        return resolucion;
    }

    /** El candidato de una resolución: clave {@code presidencia:235734:resolucion}, por su número de nodo. */
    static Candidato armar(String candidatoId, JsonNode resolucion, List<NombresDeEntidad> entidades) {
        String nodo = resolucion.path("nodo").asString();
        String titulo = resolucion.path("titulo").asString("");
        String resumenDeLaFuente = resolucion.path("resumen").asString("");
        String nombres = String.join(", ", entidades.stream().map(NombresDeEntidad::nombre).toList());
        String resumen = "Presidencia publicó el " + resolucion.path("fecha").asString() + " la " + titulo + ". Nombra a: " + nombres + ".";
        StringBuilder campos = new StringBuilder("titulo = ").append(titulo)
                .append("\nfecha = ").append(resolucion.path("fecha").asString());
        for (NombresDeEntidad entidad : entidades) {
            int donde = entidad.dondeAparece(resumenDeLaFuente);
            if (donde >= 0) {
                int desde = Math.max(0, donde - CONTEXTO / 2);
                int hasta = Math.min(resumenDeLaFuente.length(), donde + CONTEXTO);
                campos.append("\nresumen = ").append(desde > 0 ? "..." : "").append(resumenDeLaFuente, desde, hasta)
                        .append(hasta < resumenDeLaFuente.length() ? "..." : "");
                break;
            }
        }
        if (resolucion.has("pdf")) {
            campos.append("\npdf = ").append(resolucion.path("pdf").asString());
        }
        return new Candidato(candidatoId, LectorPresidencia.FUENTE, nodo, nodo, LectorPresidencia.urlPublica(nodo),
                resolucion.path("fecha").asString(), "resolucion",
                entidades.stream().map(e -> new EntidadEnCandidato("nombrada", e.entidadId(), e.nombre())).toList(),
                resumen.replaceAll("\\s+", " ").trim(), new Evidencia(campos.toString(), resolucion.path("hashContenido").asString()), 0);
    }

    /** Las palabras de los nombres de las entidades seguidas, que el tapado nunca toca. */
    static List<String> palabrasDeEntidades(List<NombresDeEntidad> entidades) {
        List<String> palabras = new ArrayList<>();
        for (NombresDeEntidad entidad : entidades) {
            palabras.add(entidad.nombre());
            palabras.addAll(entidad.nombresEnLaFuente());
        }
        return palabras;
    }

    private List<NombresDeEntidad> entidades(List<String> ids) {
        List<NombresDeEntidad> entidades = new ArrayList<>();
        for (String id : ids) {
            jdbc.sql("select nombre, identificadores from parte.entidades where id = :id").param("id", id)
                    .query((f, n) -> {
                        List<String> nombres = new ArrayList<>();
                        json.readTree(f.getString(2)).path(LectorPresidencia.FUENTE).forEach(nombre -> nombres.add(nombre.asString()));
                        return new NombresDeEntidad(id, f.getString(1), nombres);
                    })
                    .optional().ifPresent(entidades::add);
        }
        return entidades;
    }

    private void terminar(Sobre inicio, int leidas, int emitidos) {
        emisor.emitir(inicio.corridaId(), Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                new ExploracionTerminada(LectorPresidencia.FUENTE, leidas, emitidos, 0));
    }
}
