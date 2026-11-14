package io.github.andresdiegolanda.elparte.archivista;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.corrida.Corridas;
import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoCotejado;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El archivista: coteja cada candidato con la base de afirmaciones y decide si es un hecho nuevo, una
 * actualización de uno que ya está, o algo ya conocido (docs/contratos.md).
 *
 * <p>La identidad de un hecho es {@code fuente:proceso:tipoDeHecho}. Si el hecho ya está, se compara
 * la huella del contenido leído con la de su última versión: si es la misma, el candidato es conocido
 * y termina acá. En la primera rebanada el archivista no usa el modelo: con fuentes que dan un
 * identificador estable, la clave alcanza.
 *
 * <p>Solo cuentan las versiones escritas por corridas que empezaron antes que la corrida de
 * referencia: la propia, o la original si es una reproducción. Así el cotejo no depende de qué otro
 * candidato de la misma noche llegó primero, y una corrida repetida decide lo mismo que la original.
 */
@Component
public class Archivista {

    public static final String AGENTE = "archivista";

    private final JdbcClient jdbc;
    private final Emisor emisor;
    private final Procesados procesados;
    private final JsonMapper json;
    private final Corridas corridas;

    public Archivista(JdbcClient jdbc, Emisor emisor, Procesados procesados, JsonMapper json, Corridas corridas) {
        this.jdbc = jdbc;
        this.emisor = emisor;
        this.procesados = procesados;
        this.json = json;
        this.corridas = corridas;
    }

    public static String claveDeHecho(Candidato candidato) {
        return candidato.fuente() + ":" + candidato.proceso() + ":" + candidato.tipoDeHecho();
    }

    @KafkaListener(topics = Tipos.CANDIDATOS, groupId = AGENTE)
    @Transactional
    public void recibir(String valor) {
        Sobre sobre = json.readValue(valor, Sobre.class);
        if (!procesados.primeraVez(AGENTE, sobre.mensajeId())) {
            return;
        }
        Candidato candidato = json.treeToValue(sobre.contenido(), Candidato.class);
        String clave = claveDeHecho(candidato);
        CorridaIniciada corrida = corridas.contexto(sobre.corridaId());
        String referencia = corrida.reproduceA() == null ? sobre.corridaId() : corrida.reproduceA();
        Optional<Object[]> ultima = jdbc.sql("""
                select h.id, v.numero, e.hash_contenido
                from parte.hechos h
                join parte.versiones_de_hecho v on v.hecho_id = h.id
                join parte.corridas c on c.id = v.corrida_id
                left join parte.evidencias e on e.version_id = v.id
                where h.clave_de_hecho = :clave
                  and c.iniciada_en < (select iniciada_en from parte.corridas where id = :referencia)
                order by v.numero desc, e.id limit 1
                """)
                .param("clave", clave)
                .param("referencia", referencia)
                .query((fila, n) -> new Object[] { fila.getLong(1), fila.getInt(2), fila.getString(3) })
                .optional();

        CandidatoCotejado cotejo;
        if (ultima.isEmpty()) {
            cotejo = new CandidatoCotejado(candidato.candidatoId(), clave, "nuevo", null, List.of(),
                    "No hay ningún hecho con la clave " + clave + ".");
        } else {
            Long hecho = (Long) ultima.get()[0];
            String version = String.valueOf(ultima.get()[1]);
            String hashAnterior = (String) ultima.get()[2];
            if (candidato.evidencia().hashContenido().equals(hashAnterior)) {
                cotejo = new CandidatoCotejado(candidato.candidatoId(), clave, "conocido", hecho, List.of(),
                        "Es la misma publicación que respalda la versión " + version + " del hecho.");
            } else {
                cotejo = new CandidatoCotejado(candidato.candidatoId(), clave, "actualizacion", hecho,
                        List.of("Cambió el contenido leído en " + candidato.identificadorEnFuente()
                                + " respecto de la versión " + version + "."),
                        "El hecho ya está, pero lo que publicó la fuente cambió.");
            }
        }
        emisor.emitir(sobre.corridaId(), Tipos.CANDIDATOS_COTEJADOS, AGENTE, sobre.mensajeId(), 0, cotejo);
    }
}
