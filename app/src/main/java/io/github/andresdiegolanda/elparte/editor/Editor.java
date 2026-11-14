package io.github.andresdiegolanda.elparte.editor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EvidenciaConUrl;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.RevisionPendiente;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El editor escribe cada hecho analizado en la base de afirmaciones, con la traza de los cuatro
 * mensajes que lo produjeron (docs/base-de-afirmaciones.md), y manda a revisión humana todo lo
 * INFERRED (docs/contratos.md, "Umbral de revisión"). Los informes de cada cliente los arma al cerrar
 * la corrida ({@link ArmadoDeInformes}).
 *
 * <p>Una corrida de reproducción no toca la base de afirmaciones: emite los mismos mensajes, que quedan
 * en el registro, y nada más.
 */
@Component
public class Editor {

    public static final String AGENTE = "editor";

    private final JdbcClient jdbc;
    private final RegistroDeMensajes registro;
    private final Corridas corridas;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JsonMapper json;

    public Editor(JdbcClient jdbc, RegistroDeMensajes registro, Corridas corridas, Emisor emisor,
            Procesados procesados, JsonMapper json) {
        this.jdbc = jdbc;
        this.registro = registro;
        this.corridas = corridas;
        this.emisor = emisor;
        this.procesados = procesados;
        this.json = json;
    }

    @KafkaListener(topics = Tipos.HECHOS_ANALIZADOS, groupId = AGENTE)
    @Transactional
    public void recibir(String valor) {
        Sobre analizado = json.readValue(valor, Sobre.class);
        if (!procesados.primeraVez(AGENTE, analizado.mensajeId())) {
            return;
        }
        HechoAnalizado hecho = json.treeToValue(analizado.contenido(), HechoAnalizado.class);
        if ("INFERRED".equals(hecho.grado())) {
            emisor.emitir(analizado.corridaId(), Tipos.REVISION_PENDIENTE, AGENTE, analizado.mensajeId(), 0,
                    new RevisionPendiente(hecho.claveDeHecho(), hecho.grado(),
                            "Todo hecho INFERRED va a revisión en la versión 0."));
        }
        if (corridas.esReproduccion(analizado.corridaId())) {
            return;
        }
        registro.esperar(analizado.mensajeId());
        Sobre graduadoSobre = registro.esperar(analizado.causaId());
        Sobre cotejadoSobre = registro.esperar(graduadoSobre.causaId());
        Sobre candidatoSobre = registro.esperar(cotejadoSobre.causaId());
        CandidatoGraduado graduado = registro.contenido(graduadoSobre, CandidatoGraduado.class);
        Candidato candidato = registro.contenido(candidatoSobre, Candidato.class);

        jdbc.sql("""
                insert into parte.hechos (clave_de_hecho, fuente_id, tipo_de_hecho) values (:clave, :fuente, :tipo)
                on conflict (clave_de_hecho) do nothing
                """)
                .param("clave", hecho.claveDeHecho())
                .param("fuente", candidato.fuente())
                .param("tipo", candidato.tipoDeHecho())
                .update();
        long hechoId = jdbc.sql("select id from parte.hechos where clave_de_hecho = :clave for update")
                .param("clave", hecho.claveDeHecho()).query(Long.class).single();
        int numero = jdbc.sql("select coalesce(max(numero), 0) + 1 from parte.versiones_de_hecho where hecho_id = :h")
                .param("h", hechoId).query(Integer.class).single();
        long versionId = jdbc.sql("""
                insert into parte.versiones_de_hecho (hecho_id, numero, afirmacion, grado, razonamiento,
                    fecha_publicacion, fecha_confirmada, que_dice, por_que_importa, que_mirar, estado_revision,
                    corrida_id, mensaje_candidato, mensaje_cotejado, mensaje_graduado, mensaje_analizado)
                values (:hecho, :numero, :afirmacion, :grado, :razonamiento, :fecha, :confirmada, :queDice,
                    :porQue, :queMirar, :revision, :corrida, :candidato, :cotejado, :graduado, :analizado)
                returning id
                """)
                .param("hecho", hechoId)
                .param("numero", numero)
                .param("afirmacion", graduado.afirmacion())
                .param("grado", hecho.grado())
                .param("razonamiento", graduado.razonamiento())
                .param("fecha", candidato.fechaPublicacion())
                .param("confirmada", graduado.fechaConfirmada())
                .param("queDice", hecho.queDice())
                .param("porQue", hecho.porQueImporta())
                .param("queMirar", hecho.queMirar())
                .param("revision", "INFERRED".equals(hecho.grado()) ? "pendiente" : "no_requiere")
                .param("corrida", analizado.corridaId())
                .param("candidato", candidatoSobre.mensajeId())
                .param("cotejado", cotejadoSobre.mensajeId())
                .param("graduado", graduadoSobre.mensajeId())
                .param("analizado", analizado.mensajeId())
                .query(Long.class).single();
        for (EvidenciaConUrl evidencia : graduado.evidencia()) {
            jdbc.sql("""
                    insert into parte.evidencias (version_id, url, fragmento, hash_contenido)
                    values (:version, :url, :fragmento, :hash)
                    """)
                    .param("version", versionId)
                    .param("url", evidencia.url())
                    .param("fragmento", evidencia.fragmento())
                    .param("hash", candidato.evidencia().hashContenido())
                    .update();
        }
        if (graduado.evidencia().isEmpty()) {
            jdbc.sql("insert into parte.evidencias (version_id, url, fragmento, hash_contenido) values (:v, :u, :f, :h)")
                    .param("v", versionId)
                    .param("u", candidato.url())
                    .param("f", candidato.evidencia().fragmento())
                    .param("h", candidato.evidencia().hashContenido())
                    .update();
        }
        for (EntidadEnCandidato entidad : candidato.entidades()) {
            if (entidad.entidadId() == null) {
                continue;
            }
            jdbc.sql("""
                    insert into parte.entidades (id, tipo, nombre) values (:id, :tipo, :nombre)
                    on conflict (id) do nothing
                    """)
                    .param("id", entidad.entidadId())
                    .param("tipo", entidad.entidadId().substring(0, entidad.entidadId().indexOf(':')))
                    .param("nombre", entidad.nombre())
                    .update();
            jdbc.sql("""
                    insert into parte.hecho_entidades (hecho_id, entidad_id, rol) values (:hecho, :entidad, :rol)
                    on conflict do nothing
                    """)
                    .param("hecho", hechoId)
                    .param("entidad", entidad.entidadId())
                    .param("rol", entidad.rol())
                    .update();
        }
    }
}
