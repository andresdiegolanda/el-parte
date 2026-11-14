package io.github.andresdiegolanda.elparte.mensajes;

import java.util.List;

/**
 * Los tipos de mensaje de docs/contratos.md. Cada tipo vive en su propio tópico,
 * con el mismo nombre (ADR 0002).
 */
public final class Tipos {

    public static final String CORRIDA_INICIADA = "corrida.iniciada";
    public static final String CANDIDATOS = "candidatos";
    public static final String EXPLORACION_TERMINADA = "exploracion.terminada";
    public static final String CANDIDATOS_COTEJADOS = "candidatos.cotejados";
    public static final String CANDIDATOS_GRADUADOS = "candidatos.graduados";
    public static final String HECHOS_ANALIZADOS = "hechos.analizados";
    public static final String INFORMES = "informes";
    public static final String REVISION_PENDIENTE = "revision.pendiente";
    public static final String FUENTE_FALLIDA = "fuente.fallida";
    public static final String USO_MODELO = "uso.modelo";
    public static final String CORRIDA_CORTADA = "corrida.cortada";
    public static final String CORRIDA_TERMINADA = "corrida.terminada";

    public static final List<String> TODOS = List.of(
            CORRIDA_INICIADA, CANDIDATOS, EXPLORACION_TERMINADA, CANDIDATOS_COTEJADOS, CANDIDATOS_GRADUADOS,
            HECHOS_ANALIZADOS, INFORMES, REVISION_PENDIENTE, FUENTE_FALLIDA, USO_MODELO, CORRIDA_CORTADA,
            CORRIDA_TERMINADA);

    /** Versión del contrato de cada tipo. Todos están en la 1. */
    public static final int VERSION = 1;

    private Tipos() {
    }
}
