package io.github.andresdiegolanda.elparte.lectura;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.grabaciones.Grabaciones;
import io.github.andresdiegolanda.elparte.infraestructura.Huellas;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.lectura.ControlDeObservaciones.HechoLeido;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionGraduada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionPropuesta;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionesGraduadas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionesPropuestas;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.modelo.Modelo;
import io.github.andresdiegolanda.elparte.presupuesto.SinPresupuesto;
import io.github.andresdiegolanda.elparte.refutador.RefutadorDeObservaciones;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El lector de expedientes (ADR 0018, T47): en una corrida de lectura, lee de una vez los hechos publicados
 * de una entidad en una ventana y le pide al modelo observaciones que una publicación sola no muestra,
 * cada una citando hechos. Las controla en código, se las pasa al refutador para que las gradúe contra
 * los hechos citados, guarda las que se sostienen y cierra la corrida.
 *
 * <p>Lee hechos y no fuentes: ya están tapados y graduados. La entrada se graba como una lectura de
 * fuente, así la repetición lee los mismos hechos aunque la base haya cambiado (ADR 0011).
 */
@Component
public class LectorDeExpedientes {

    public static final String AGENTE = "lector-de-expedientes";
    private static final Logger log = LoggerFactory.getLogger(LectorDeExpedientes.class);
    /** Unos 120.000 tokens de entrada: si la ventana tiene más, se cortan los hechos más viejos. */
    static final int MAXIMO_DE_CARACTERES = 400_000;
    private static final int TOKENS_DE_SALIDA = 4000;
    private static final int MAXIMO_DE_OBSERVACIONES = 10;

    static final String SISTEMA = """
            Sos el lector de expedientes de El Parte. Recibís los hechos ya publicados de una entidad en una \
            ventana de tiempo, uno por línea: clave, noche, grado y afirmación. Buscá lo que una publicación sola \
            no muestra y escribí observaciones.

            Cada observación es de una de estas clases:
            - repeticion: un mismo objeto que se compra o se pide varias veces.
            - cadena: un mismo proceso con cambios sucesivos, como prórrogas, ajustes o aclaraciones.
            - proveedor: un mismo proveedor en varias adjudicaciones.
            - otra: algo que se repite y no es de las tres anteriores.

            Reglas:
            - Cada observación cita en "hechos" las claves de dos hechos o más, copiadas tal cual de la entrada.
            - Decí qué se repite, cuántas veces y cuándo. No califiques (nada de "irregular", "sospechoso" ni \
            "favorece"), no recomiendes y no expliques causas.
            - No pongas cifras que no estén en las afirmaciones que citás.
            - Si no ves nada que se repita, devolvé la lista vacía. Mejor pocas observaciones bien citadas que muchas.
            - Como mucho diez observaciones.
            - Donde dice [persona] hay un dato personal tapado: no lo reconstruyas ni lo menciones.
            - Escribí en español rioplatense y sin guiones largos.
            """;

    private final Corridas corridas;
    private final Grabaciones grabaciones;
    private final Modelo modelo;
    private final RefutadorDeObservaciones refutador;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock reloj;

    public LectorDeExpedientes(Corridas corridas, Grabaciones grabaciones, Modelo modelo, RefutadorDeObservaciones refutador,
            Emisor emisor, Procesados procesados, JdbcClient jdbc, JsonMapper json, Clock reloj) {
        this.corridas = corridas;
        this.grabaciones = grabaciones;
        this.modelo = modelo;
        this.refutador = refutador;
        this.emisor = emisor;
        this.procesados = procesados;
        this.jdbc = jdbc;
        this.json = json;
        this.reloj = reloj;
    }

    /** Lo que el modelo devuelve. */
    public record Lectura(List<Propuesta> observaciones) {
    }

    public record Propuesta(String clase, String texto, List<String> hechos) {
    }

    private record Ventana(String entidad, String nombre, String desde, String hasta) {
    }

    @KafkaListener(topics = Tipos.CORRIDA_INICIADA, groupId = AGENTE)
    public void recibir(String valor) {
        Sobre inicio = json.readValue(valor, Sobre.class);
        if (corridas.lecturaQueHace(inicio.corridaId()).isEmpty() || procesados.yaProcesado(AGENTE, inicio.mensajeId())) {
            return;
        }
        leer(inicio);
        procesados.primeraVez(AGENTE, inicio.mensajeId());
    }

    private void leer(Sobre inicio) {
        String corridaId = inicio.corridaId();
        Instant empezo = reloj.instant();
        Ventana ventana = jdbc.sql("""
                select l.entidad_id, e.nombre, l.desde::text, l.hasta::text from parte.lecturas_de_expediente l
                join parte.entidades e on e.id = l.entidad_id where l.corrida_id = :id
                """).param("id", corridaId)
                .query((f, n) -> new Ventana(f.getString(1), f.getString(2), f.getString(3), f.getString(4))).single();
        JsonNode entrada = grabaciones.conGrabacion(corridaId,
                "expediente:" + ventana.entidad() + ":" + ventana.desde() + ":" + ventana.hasta(), "fuente", () -> hechos(ventana));
        Map<String, HechoLeido> leidos = new LinkedHashMap<>();
        for (JsonNode h : entrada.path("hechos")) {
            leidos.put(h.path("clave").asString(), new HechoLeido(h.path("clave").asString(), h.path("noche").asString(),
                    h.path("grado").asString(), h.path("afirmacion").asString()));
        }
        int cortados = entrada.path("cortados").asInt(0);
        String huella = entrada.path("huella").asString();

        List<Propuesta> respuesta = List.of();
        String limite = null;
        if (leidos.size() >= 2) {
            try {
                Lectura lectura = modelo.pedir(new Modelo.Pedido(corridaId, AGENTE, inicio.mensajeId(), 1, SISTEMA,
                        pedido(ventana, leidos.values(), cortados), TOKENS_DE_SALIDA), Lectura.class);
                respuesta = lectura.observaciones() == null ? List.of() : lectura.observaciones();
            } catch (Modelo.FallaDelModelo falla) {
                log.warn("La lectura {} no tuvo respuesta del modelo: {}", corridaId, falla.getMessage());
            } catch (SinPresupuesto sinPresupuesto) {
                limite = sinPresupuesto.limite();
            }
        }
        List<ObservacionPropuesta> propuestas = new ArrayList<>();
        for (int i = 0; i < respuesta.size() && i < MAXIMO_DE_OBSERVACIONES; i++) {
            Propuesta p = respuesta.get(i);
            List<String> hechos = p.hechos() == null ? List.of() : p.hechos().stream().map(String::trim).distinct().toList();
            String clase = p.clase() == null ? null : p.clase().trim();
            propuestas.add(new ObservacionPropuesta(i + 1, clase, p.texto() == null ? "" : p.texto().trim(), hechos,
                    ControlDeObservaciones.descarte(clase, p.texto(), hechos, leidos)));
        }
        Sobre propuestasEmitidas = emisor.emitir(corridaId, Tipos.OBSERVACIONES_PROPUESTAS, AGENTE, inicio.mensajeId(), 0,
                new ObservacionesPropuestas(ventana.entidad(), ventana.desde(), ventana.hasta(), leidos.size(), cortados, huella,
                        propuestas));

        List<ObservacionGraduada> graduadas = new ArrayList<>();
        for (ObservacionPropuesta propuesta : propuestas) {
            if (propuesta.descarte() != null || limite != null) {
                continue;
            }
            try {
                graduadas.add(refutador.graduar(corridaId, propuestasEmitidas.mensajeId(), propuesta,
                        propuesta.hechos().stream().map(leidos::get).toList(),
                        ControlDeObservaciones.gradoMaximo(propuesta.hechos(), leidos)));
            } catch (SinPresupuesto sinPresupuesto) {
                limite = sinPresupuesto.limite();
            }
        }
        List<ObservacionGraduada> sostenidas = graduadas.stream().filter(g -> !"NOT_MENTIONED".equals(g.grado())).toList();
        emisor.emitir(corridaId, Tipos.OBSERVACIONES_GRADUADAS, AGENTE, propuestasEmitidas.mensajeId(), 0,
                new ObservacionesGraduadas(graduadas, propuestas.size() - sostenidas.size()));
        guardar(corridaId, leidos.size(), cortados, huella, propuestas.size(), sostenidas);
        cerrar(inicio, empezo, limite, leidos.size(), propuestas.size(), sostenidas.size());
    }

    /** Las últimas versiones publicadas de los hechos de la ventana, sin las rechazadas en la revisión. */
    private ObjectNode hechos(Ventana ventana) {
        List<HechoLeido> todos = jdbc.sql("""
                with primeras as (
                    select h.id, h.clave_de_hecho, min(c.fecha_objetivo) as noche
                    from parte.hechos h
                    join parte.hecho_entidades he on he.hecho_id = h.id and he.entidad_id = :entidad
                    join parte.versiones_de_hecho v on v.hecho_id = h.id
                    join parte.corridas c on c.id = v.corrida_id
                    group by h.id, h.clave_de_hecho
                ), ultimas as (
                    select distinct on (v.hecho_id) v.hecho_id, v.grado, v.afirmacion, v.estado_revision
                    from parte.versiones_de_hecho v order by v.hecho_id, v.numero desc
                )
                select p.clave_de_hecho, p.noche::text, u.grado, u.afirmacion
                from primeras p join ultimas u on u.hecho_id = p.id
                where p.noche between cast(:desde as date) and cast(:hasta as date) and u.estado_revision <> 'rechazado'
                order by p.noche, p.clave_de_hecho
                """).param("entidad", ventana.entidad()).param("desde", ventana.desde()).param("hasta", ventana.hasta())
                .query((f, n) -> new HechoLeido(f.getString(1), f.getString(2), f.getString(3), f.getString(4))).list();
        // Si no entran todos, se cortan los más viejos.
        int desde = 0;
        long largo = todos.stream().mapToLong(h -> linea(h).length()).sum();
        while (largo > MAXIMO_DE_CARACTERES && desde < todos.size()) {
            largo -= linea(todos.get(desde)).length();
            desde++;
        }
        List<HechoLeido> entran = todos.subList(desde, todos.size());
        ArrayNode lista = json.createArrayNode();
        StringBuilder lineas = new StringBuilder();
        for (HechoLeido h : entran) {
            lista.add(json.createObjectNode().put("clave", h.clave()).put("noche", h.noche()).put("grado", h.grado())
                    .put("afirmacion", h.afirmacion()));
            lineas.append(linea(h));
        }
        ObjectNode entrada = json.createObjectNode();
        entrada.set("hechos", lista);
        entrada.put("cortados", desde);
        entrada.put("huella", "sha256:" + Huellas.sha256(lineas.toString()));
        return entrada;
    }

    static String linea(HechoLeido h) {
        return h.clave() + " | " + h.noche() + " | " + h.grado() + " | " + h.afirmacion().replace('\n', ' ') + "\n";
    }

    private static String pedido(Ventana ventana, Iterable<HechoLeido> hechos, int cortados) {
        StringBuilder texto = new StringBuilder("Hechos del expediente de ").append(ventana.nombre()).append(", del ")
                .append(ventana.desde()).append(" al ").append(ventana.hasta());
        if (cortados > 0) {
            texto.append(" (sin los ").append(cortados).append(" más viejos, que no entran)");
        }
        texto.append(":\n");
        hechos.forEach(h -> texto.append(linea(h)));
        return texto.toString();
    }

    private void guardar(String corridaId, int leidos, int cortados, String huella, int propuestas,
            List<ObservacionGraduada> sostenidas) {
        jdbc.sql("""
                update parte.lecturas_de_expediente set hechos_leidos = :leidos, hechos_cortados = :cortados,
                       huella_de_los_hechos = :huella, propuestas = :propuestas, descartadas = :descartadas
                where corrida_id = :id
                """).param("leidos", leidos).param("cortados", cortados).param("huella", huella).param("propuestas", propuestas)
                .param("descartadas", propuestas - sostenidas.size()).param("id", corridaId).update();
        int numero = 0;
        for (ObservacionGraduada o : sostenidas) {
            jdbc.sql("""
                    insert into parte.observaciones (corrida_id, numero, clase, texto, grado, razonamiento, hechos)
                    values (:id, :numero, :clase, :texto, :grado, :razonamiento, cast(:hechos as text[]))
                    """).param("id", corridaId).param("numero", ++numero).param("clase", o.clase()).param("texto", o.texto())
                    .param("grado", o.grado()).param("razonamiento", o.razonamiento())
                    .param("hechos", "{" + String.join(",", o.hechos().stream().map(h -> "\"" + h.replace("\"", "") + "\"").toList()) + "}")
                    .update();
        }
    }

    private void cerrar(Sobre inicio, Instant empezo, String limite, int leidos, int propuestas, int sostenidas) {
        String corridaId = inicio.corridaId();
        String estado = limite == null ? "completa" : "cortada";
        int cerradas = jdbc.sql("""
                update parte.corridas set estado = :estado, limite_alcanzado = :limite, terminada_en = :fin
                where id = :id and estado = 'en_curso'
                """).param("estado", estado).param("limite", limite).param("fin", Timestamp.from(reloj.instant()))
                .param("id", corridaId).update();
        if (cerradas == 0) {
            return;
        }
        BigDecimal costo = jdbc.sql("select gastado_usd from parte.libros_de_corrida where corrida_id = :id").param("id", corridaId)
                .query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);
        Map<String, Integer> conteos = new LinkedHashMap<>();
        conteos.put("hechosLeidos", leidos);
        conteos.put("propuestas", propuestas);
        conteos.put("sostenidas", sostenidas);
        conteos.put("descartadas", propuestas - sostenidas);
        emisor.emitir(corridaId, Tipos.CORRIDA_TERMINADA, AGENTE, Corridas.idDeInicio(corridaId), 0,
                new CorridaTerminada(estado, conteos, costo, Duration.between(empezo, reloj.instant()).toSeconds()));
        log.info("Lectura {} {}: {}", corridaId, estado, conteos);
    }
}
