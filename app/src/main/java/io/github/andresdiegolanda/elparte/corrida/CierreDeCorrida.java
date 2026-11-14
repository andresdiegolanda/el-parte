package io.github.andresdiegolanda.elparte.corrida;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.editor.ArmadoDeInformes;
import io.github.andresdiegolanda.elparte.editor.Editor;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoCotejado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaCortada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ExploracionTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * Cierra cada corrida cuando todos sus candidatos llegaron a un final, o la corta cuando pasa su tope
 * de tiempo (ADR 0005; el tope de costo y el de pasos llegan con T22).
 *
 * <p>Un candidato llega a un final cuando el archivista lo da por conocido, cuando el refutador lo
 * gradúa NOT_MENTIONED o cuando el editor procesó su análisis. Todo se cuenta desde el registro de
 * mensajes, así una reproducción cierra igual que la original.
 */
@Component
public class CierreDeCorrida {

    private static final Logger log = LoggerFactory.getLogger(CierreDeCorrida.class);

    private final JdbcClient jdbc;
    private final RegistroDeMensajes registro;
    private final Corridas corridas;
    private final Emisor emisor;
    private final ArmadoDeInformes informes;
    private final Clock reloj;

    public CierreDeCorrida(JdbcClient jdbc, RegistroDeMensajes registro, Corridas corridas, Emisor emisor,
            ArmadoDeInformes informes, Clock reloj) {
        this.jdbc = jdbc;
        this.registro = registro;
        this.corridas = corridas;
        this.emisor = emisor;
        this.informes = informes;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${elparte.corrida.revisar-cierre-ms:2000}")
    public void revisar() {
        for (String corridaId : jdbc.sql("select id from parte.corridas where estado = 'en_curso' order by iniciada_en")
                .query(String.class).list()) {
            try {
                revisar(corridaId);
            } catch (RuntimeException e) {
                log.warn("No se pudo revisar el cierre de {}: {}", corridaId, e.getMessage());
            }
        }
    }

    void revisar(String corridaId) {
        if (registro.buscar(Corridas.idDeInicio(corridaId)).isEmpty()) {
            return;
        }
        CorridaIniciada corrida = corridas.contexto(corridaId);
        Instant iniciada = jdbc.sql("select iniciada_en from parte.corridas where id = :id").param("id", corridaId)
                .query(Timestamp.class).single().toInstant();
        Conteo conteo = contar(corridaId);
        // El total sale de exploracion.terminada y no de contar candidatos: el observador guarda los
        // mensajes de cada tópico a su ritmo, y un candidato puede llegar a la tabla después.
        boolean explorada = conteo.exploraciones >= corrida.fuentes().size();
        if (explorada && conteo.finales() >= conteo.esperados) {
            cerrar(corridaId, corrida, iniciada, conteo, null);
        } else if (Duration.between(iniciada, reloj.instant()).toMinutes() >= corrida.presupuesto().minutosMaximos()) {
            cerrar(corridaId, corrida, iniciada, conteo, "tiempo");
        }
    }

    private void cerrar(String corridaId, CorridaIniciada corrida, Instant iniciada, Conteo conteo, String limite) {
        String estado = limite == null ? "completa" : "cortada";
        int actualizadas = jdbc.sql("""
                update parte.corridas set estado = :estado, limite_alcanzado = :limite, terminada_en = :fin
                where id = :id and estado = 'en_curso'
                """)
                .param("estado", estado)
                .param("limite", limite)
                .param("fin", Timestamp.from(reloj.instant()))
                .param("id", corridaId)
                .update();
        if (actualizadas == 0) {
            return;
        }
        String inicio = Corridas.idDeInicio(corridaId);
        int sinProcesar = conteo.sinProcesarEnExploracion + Math.max(0, conteo.esperados - conteo.finales());
        long minutos = Duration.between(iniciada, reloj.instant()).toMinutes();
        if (limite != null) {
            emisor.emitir(corridaId, Tipos.CORRIDA_CORTADA, Editor.AGENTE, inicio, 0,
                    new CorridaCortada(limite, conteo.costo, conteo.llamadas, minutos, sinProcesar));
        }
        informes.armar(corridaId, limite != null);
        Map<String, Integer> conteos = new LinkedHashMap<>();
        conteos.put("candidatos", conteo.esperados);
        conteos.put("conocidos", conteo.conocidos);
        conteos.put("actualizaciones", conteo.actualizaciones);
        conteos.put("nuevos", conteo.nuevos);
        conteos.put("PROVEN", conteo.proven);
        conteos.put("INFERRED", conteo.inferred);
        conteos.put("NOT_MENTIONED", conteo.notMentioned);
        conteos.put("sinProcesar", sinProcesar);
        emisor.emitir(corridaId, Tipos.CORRIDA_TERMINADA, Editor.AGENTE, inicio, 0,
                new CorridaTerminada(estado, conteos, conteo.costo, Duration.between(iniciada, reloj.instant()).toSeconds()));
        log.info("Corrida {} {}: {}", corridaId, estado, conteos);
    }

    private Conteo contar(String corridaId) {
        Conteo conteo = new Conteo();
        for (Sobre sobre : registro.deCorrida(corridaId)) {
            switch (sobre.tipo()) {
                case Tipos.EXPLORACION_TERMINADA -> {
                    ExploracionTerminada exploracion = registro.contenido(sobre, ExploracionTerminada.class);
                    conteo.exploraciones++;
                    conteo.esperados += exploracion.candidatosEmitidos();
                    conteo.sinProcesarEnExploracion += exploracion.sinProcesar();
                }
                case Tipos.CANDIDATOS_COTEJADOS -> {
                    switch (registro.contenido(sobre, CandidatoCotejado.class).estado()) {
                        case "conocido" -> conteo.conocidos++;
                        case "actualizacion" -> conteo.actualizaciones++;
                        default -> conteo.nuevos++;
                    }
                }
                case Tipos.CANDIDATOS_GRADUADOS -> {
                    switch (registro.contenido(sobre, CandidatoGraduado.class).grado()) {
                        case "PROVEN" -> conteo.proven++;
                        case "INFERRED" -> conteo.inferred++;
                        default -> conteo.notMentioned++;
                    }
                }
                case Tipos.HECHOS_ANALIZADOS -> conteo.analizados.add(sobre.mensajeId());
                case Tipos.USO_MODELO -> {
                    conteo.llamadas++;
                    conteo.costo = conteo.costo.add(registro.contenido(sobre, UsoModelo.class).costoUsd());
                }
                default -> {
                }
            }
        }
        if (!conteo.analizados.isEmpty()) {
            conteo.editados = jdbc.sql("""
                    select count(*) from parte.mensajes_procesados where consumidor = :editor and mensaje_id in (:ids)
                    """)
                    .param("editor", Editor.AGENTE)
                    .param("ids", conteo.analizados)
                    .query(Integer.class).single();
        }
        return conteo;
    }

    private static final class Conteo {
        int exploraciones;
        int sinProcesarEnExploracion;
        int esperados;
        int conocidos;
        int actualizaciones;
        int nuevos;
        int proven;
        int inferred;
        int notMentioned;
        int editados;
        int llamadas;
        BigDecimal costo = BigDecimal.ZERO;
        final List<String> analizados = new java.util.ArrayList<>();

        int finales() {
            return conocidos + notMentioned + editados;
        }
    }
}
