package io.github.andresdiegolanda.elparte.analista;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.andresdiegolanda.elparte.infraestructura.Procesados;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;
import io.github.andresdiegolanda.elparte.mensajes.Emisor;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El analista, en su versión mínima de la primera rebanada (docs/plan.md): pasa la afirmación tal cual
 * y marca "sin análisis", para respetar el contrato. El análisis de verdad (qué dice, por qué importa y
 * qué mirar) llega con T21. El grado se copia tal cual: el analista no lo puede cambiar.
 */
@Component
public class Analista {

    public static final String AGENTE = "analista";
    static final String SIN_ANALISIS = "Sin análisis: en la primera rebanada el analista solo pasa la afirmación.";

    private final Emisor emisor;
    private final Procesados procesados;
    private final JsonMapper json;

    public Analista(Emisor emisor, Procesados procesados, JsonMapper json) {
        this.emisor = emisor;
        this.procesados = procesados;
        this.json = json;
    }

    @KafkaListener(topics = Tipos.CANDIDATOS_GRADUADOS, groupId = AGENTE)
    @Transactional
    public void recibir(String valor) {
        Sobre sobre = json.readValue(valor, Sobre.class);
        CandidatoGraduado graduado = json.treeToValue(sobre.contenido(), CandidatoGraduado.class);
        if ("NOT_MENTIONED".equals(graduado.grado()) || !procesados.primeraVez(AGENTE, sobre.mensajeId())) {
            return;
        }
        emisor.emitir(sobre.corridaId(), Tipos.HECHOS_ANALIZADOS, AGENTE, sobre.mensajeId(), 0,
                new HechoAnalizado(graduado.candidatoId(), graduado.claveDeHecho(), graduado.grado(),
                        graduado.afirmacion(), SIN_ANALISIS, SIN_ANALISIS));
    }
}
