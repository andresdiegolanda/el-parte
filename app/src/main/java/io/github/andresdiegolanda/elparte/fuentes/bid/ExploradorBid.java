package io.github.andresdiegolanda.elparte.fuentes.bid;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.fuentes.NombresDeEntidad;
import io.github.andresdiegolanda.elparte.fuentes.bid.LectorBid.FallaDelBid;
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
 * El explorador del BID (ADR 0006): las operaciones del BID para Uruguay aprobadas la noche objetivo, y un
 * candidato por cada una que nombra a una entidad seguida, en su nombre o en su objetivo. Los textos del
 * BID están en inglés, así que una entidad se encuentra sobre todo por su sigla.
 *
 * <p>El BID da el estado de hoy, no el de esa noche (D1): repetir una noche vieja trae lo que hoy se sabe
 * de las operaciones aprobadas ese día. Lo leído se graba, así la corrida se repite sin red.
 */
@Component
public class ExploradorBid {

    public static final String AGENTE = "explorador-bid";
    private static final Logger log = LoggerFactory.getLogger(ExploradorBid.class);
    private static final int POSICION_DE_FALLAS = 100_000;
    private static final int CONTEXTO = 300;

    private final LectorBid lector;
    private final Grabaciones grabaciones;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ExploradorBid(LectorBid lector, Grabaciones grabaciones, Emisor emisor, Procesados procesados, JdbcClient jdbc,
            JsonMapper json) {
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
        if (!corrida.fuentes().contains(LectorBid.FUENTE) || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        explorar(inicio, corrida);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void explorar(Sobre inicio, CorridaIniciada corrida) {
        String corridaId = inicio.corridaId();
        LocalDate fecha = LocalDate.parse(corrida.fechaObjetivo());
        JsonNode aprobadas = grabaciones.conGrabacion(corridaId, "bid:aprobadas:" + fecha, "fuente", () -> {
            try {
                ArrayNode lista = json.createArrayNode();
                lector.aprobadas(fecha).forEach(leido -> lista.add(json.createObjectNode()
                        .<ObjectNode>set("proyecto", leido.proyecto()).put("hashContenido", leido.hashContenido())));
                return json.createObjectNode().set("proyectos", lista);
            } catch (FallaDelBid falla) {
                return json.createObjectNode().put("falla", falla.tipoDeFalla()).put("url", falla.url())
                        .put("detalle", falla.getMessage());
            }
        });
        if (aprobadas.has("falla")) {
            emisor.emitir(corridaId, Tipos.FUENTE_FALLIDA, AGENTE, inicio.mensajeId(), POSICION_DE_FALLAS,
                    new FuenteFallida(LectorBid.FUENTE, aprobadas.path("url").asString(), aprobadas.path("falla").asString(),
                            aprobadas.path("detalle").asString()));
            terminar(inicio, 0, 0, 0);
            return;
        }
        List<NombresDeEntidad> seguidas = entidades(corrida.entidadesSeguidas());
        List<Candidato> candidatos = new ArrayList<>();
        int leidos = 0;
        for (JsonNode guardado : aprobadas.path("proyectos")) {
            leidos++;
            JsonNode proyecto = guardado.path("proyecto");
            String texto = proyecto.path("oper_nm").asString("") + "\n" + proyecto.path("objtv").asString("");
            List<NombresDeEntidad> nombradas = seguidas.stream().filter(e -> e.aparece(texto)).toList();
            if (!nombradas.isEmpty()) {
                candidatos.add(armar(String.format("k-bid-%04d", candidatos.size() + 1), proyecto, nombradas,
                        guardado.path("hashContenido").asString(), fecha));
            }
        }
        for (int i = 0; i < candidatos.size(); i++) {
            emisor.emitir(corridaId, Tipos.CANDIDATOS, AGENTE, inicio.mensajeId(), i + 1, candidatos.get(i));
        }
        log.info("Exploración del BID de {}: {} operaciones aprobadas, {} candidatos", corridaId, leidos, candidatos.size());
        terminar(inicio, leidos, candidatos.size(), 0);
    }

    /** El candidato de una operación aprobada: clave {@code bid:UR-L1222:aprobacion}. */
    static Candidato armar(String candidatoId, JsonNode proyecto, List<NombresDeEntidad> entidades, String hash,
            LocalDate fecha) {
        String numero = proyecto.path("oper_num").asString();
        String nombres = String.join(", ", entidades.stream().map(NombresDeEntidad::nombre).toList());
        String resumen = "El BID aprobó el " + fecha + " la operación " + numero + ", \"" + proyecto.path("oper_nm").asString("").trim()
                + "\" (" + proyecto.path("opertyp_nm").asString("").trim() + "). Nombra a: " + nombres + ".";
        StringBuilder campos = new StringBuilder("oper_num = ").append(numero)
                .append("\noper_nm = ").append(proyecto.path("oper_nm").asString("").trim())
                .append("\napprvl_dt = ").append(proyecto.path("apprvl_dt").asString(""));
        String objetivo = proyecto.path("objtv").asString("");
        for (NombresDeEntidad entidad : entidades) {
            int donde = entidad.dondeAparece(objetivo);
            if (donde >= 0) {
                int desde = Math.max(0, donde - CONTEXTO / 2);
                int hasta = Math.min(objetivo.length(), donde + CONTEXTO);
                campos.append("\nobjtv = ").append(desde > 0 ? "..." : "").append(objetivo, desde, hasta)
                        .append(hasta < objetivo.length() ? "..." : "");
            }
        }
        return new Candidato(candidatoId, LectorBid.FUENTE, numero, numero, LectorBid.urlPublica(numero), fecha.toString(),
                "aprobacion", entidades.stream().map(e -> new EntidadEnCandidato("nombrada", e.entidadId(), e.nombre())).toList(),
                resumen.replaceAll("\\s+", " ").trim(), new Evidencia(campos.toString(), hash), 0);
    }

    private List<NombresDeEntidad> entidades(List<String> ids) {
        List<NombresDeEntidad> entidades = new ArrayList<>();
        for (String id : ids) {
            jdbc.sql("select nombre, identificadores from parte.entidades where id = :id").param("id", id)
                    .query((f, n) -> {
                        List<String> paraElBid = new ArrayList<>();
                        json.readTree(f.getString(2)).path("bid").forEach(nombre -> paraElBid.add(nombre.asString()));
                        return new NombresDeEntidad(id, f.getString(1), paraElBid);
                    })
                    .optional().ifPresent(entidades::add);
        }
        return entidades;
    }

    private void terminar(Sobre inicio, int leidos, int emitidos, int sinProcesar) {
        emisor.emitir(inicio.corridaId(), Tipos.EXPLORACION_TERMINADA, AGENTE, inicio.mensajeId(), 0,
                new ExploracionTerminada(LectorBid.FUENTE, leidos, emitidos, sinProcesar));
    }
}
