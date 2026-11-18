package io.github.andresdiegolanda.elparte.fuentes.bancomundial;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.fuentes.NombresDeEntidad;
import io.github.andresdiegolanda.elparte.fuentes.bancomundial.LectorBancoMundial.FallaDelBancoMundial;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
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
 * El explorador del Banco Mundial (ADR 0006): los proyectos para Uruguay que el directorio aprobó la noche
 * objetivo, y un candidato por cada uno que nombra a una entidad seguida en su nombre, su objetivo, su
 * resumen, su prestatario o su agencia ejecutora. Los textos están en inglés, así que una entidad se
 * encuentra sobre todo por su sigla o por su nombre en inglés.
 *
 * <p>Como el BID, da el estado de hoy, no el de esa noche (D1). Lo leído se graba, así la corrida se repite
 * sin red.
 */
@Component
public class ExploradorBancoMundial {

    public static final String AGENTE = "explorador-banco-mundial";
    private static final Logger log = LoggerFactory.getLogger(ExploradorBancoMundial.class);
    private static final int POSICION_DE_FALLAS = 100_000;
    private static final int CONTEXTO = 300;
    /** Dónde se busca a cada entidad, en este orden. */
    private static final List<String> TEXTOS = List.of("project_name", "impagency", "borrower", "pdo", "project_abstract");

    private final LectorBancoMundial lector;
    private final Grabaciones grabaciones;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ExploradorBancoMundial(LectorBancoMundial lector, Grabaciones grabaciones, Emisor emisor, Procesados procesados,
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
        if (!corrida.fuentes().contains(LectorBancoMundial.FUENTE) || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        explorar(inicio, corrida);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void explorar(Sobre inicio, CorridaIniciada corrida) {
        String corridaId = inicio.corridaId();
        LocalDate fecha = LocalDate.parse(corrida.fechaObjetivo());
        JsonNode aprobados = grabaciones.conGrabacion(corridaId, "banco-mundial:aprobados:" + fecha, "fuente", () -> {
            try {
                ArrayNode lista = json.createArrayNode();
                lector.aprobados(fecha).forEach(leido -> lista.add(json.createObjectNode()
                        .<ObjectNode>set("proyecto", leido.proyecto()).put("hashContenido", leido.hashContenido())));
                return json.createObjectNode().set("proyectos", lista);
            } catch (FallaDelBancoMundial falla) {
                return json.createObjectNode().put("falla", falla.tipoDeFalla()).put("url", falla.url())
                        .put("detalle", falla.getMessage());
            }
        });
        if (aprobados.has("falla")) {
            emisor.emitir(corridaId, Tipos.FUENTE_FALLIDA, AGENTE, inicio.mensajeId(), POSICION_DE_FALLAS,
                    new FuenteFallida(LectorBancoMundial.FUENTE, aprobados.path("url").asString(),
                            aprobados.path("falla").asString(), aprobados.path("detalle").asString()));
            terminar(inicio, 0, 0);
            return;
        }
        List<NombresDeEntidad> seguidas = entidades(corrida.entidadesSeguidas());
        List<Candidato> candidatos = new ArrayList<>();
        int leidos = 0;
        for (JsonNode guardado : aprobados.path("proyectos")) {
            leidos++;
            JsonNode proyecto = guardado.path("proyecto");
            String texto = String.join("\n", TEXTOS.stream().map(campo -> proyecto.path(campo).asString("")).toList());
            List<NombresDeEntidad> nombradas = seguidas.stream().filter(e -> e.aparece(texto)).toList();
            if (!nombradas.isEmpty()) {
                candidatos.add(armar(String.format("k-banco-mundial-%04d", candidatos.size() + 1), proyecto, nombradas,
                        guardado.path("hashContenido").asString(), fecha));
            }
        }
        for (int i = 0; i < candidatos.size(); i++) {
            emisor.emitir(corridaId, Tipos.CANDIDATOS, AGENTE, inicio.mensajeId(), i + 1, candidatos.get(i));
        }
        log.info("Exploración del Banco Mundial de {}: {} proyectos aprobados, {} candidatos", corridaId, leidos,
                candidatos.size());
        terminar(inicio, leidos, candidatos.size());
    }

    /** El candidato de un proyecto aprobado: clave {@code banco-mundial:P513691:aprobacion}. */
    static Candidato armar(String candidatoId, JsonNode proyecto, List<NombresDeEntidad> entidades, String hash,
            LocalDate fecha) {
        String id = proyecto.path("id").asString();
        String nombre = proyecto.path("project_name").asString("").trim();
        String nombres = String.join(", ", entidades.stream().map(NombresDeEntidad::nombre).toList());
        String resumen = "El Banco Mundial aprobó el " + fecha + " el proyecto " + id + ", \"" + nombre + "\". Nombra a: "
                + nombres + ".";
        StringBuilder campos = new StringBuilder("id = ").append(id)
                .append("\nproject_name = ").append(nombre)
                .append("\nboardapprovaldate = ").append(proyecto.path("boardapprovaldate").asString(""));
        for (NombresDeEntidad entidad : entidades) {
            for (String campo : TEXTOS.subList(1, TEXTOS.size())) {
                String valor = proyecto.path(campo).asString("");
                int donde = entidad.dondeAparece(valor);
                if (donde >= 0) {
                    int desde = Math.max(0, donde - CONTEXTO / 2);
                    int hasta = Math.min(valor.length(), donde + CONTEXTO);
                    campos.append('\n').append(campo).append(" = ").append(desde > 0 ? "..." : "").append(valor, desde, hasta)
                            .append(hasta < valor.length() ? "..." : "");
                    break;
                }
            }
        }
        return new Candidato(candidatoId, LectorBancoMundial.FUENTE, id, id, LectorBancoMundial.urlPublica(id), fecha.toString(),
                "aprobacion", entidades.stream().map(e -> new EntidadEnCandidato("nombrada", e.entidadId(), e.nombre())).toList(),
                resumen.replaceAll("\\s+", " ").trim(), new Evidencia(campos.toString(), hash), 0);
    }

    private List<NombresDeEntidad> entidades(List<String> ids) {
        List<NombresDeEntidad> entidades = new ArrayList<>();
        for (String id : ids) {
            jdbc.sql("select nombre, identificadores from parte.entidades where id = :id").param("id", id)
                    .query((f, n) -> {
                        List<String> nombres = new ArrayList<>();
                        json.readTree(f.getString(2)).path(LectorBancoMundial.FUENTE).forEach(nombre -> nombres.add(nombre.asString()));
                        return new NombresDeEntidad(id, f.getString(1), nombres);
                    })
                    .optional().ifPresent(entidades::add);
        }
        return entidades;
    }

    private void terminar(Sobre inicio, int leidos, int emitidos) {
        emisor.emitir(inicio.corridaId(), Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                new ExploracionTerminada(LectorBancoMundial.FUENTE, leidos, emitidos, 0));
    }
}
