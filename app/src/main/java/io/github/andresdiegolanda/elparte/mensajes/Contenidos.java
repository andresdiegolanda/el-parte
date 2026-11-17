package io.github.andresdiegolanda.elparte.mensajes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * El contenido de cada tipo de mensaje, tal como lo describe docs/contratos.md.
 * Los nombres de campo van en español y sin tildes (D7).
 */
public final class Contenidos {

    private Contenidos() {
    }

    public record Presupuesto(BigDecimal costoMaximoUsd, int pasosMaximos, int minutosMaximos) {
    }

    /** Arranca la noche. {@code reproduceA} es la corrida que se repite, solo en modo reproducción. */
    public record CorridaIniciada(
            String modo,
            String fechaObjetivo,
            List<String> fuentes,
            List<String> entidadesSeguidas,
            Presupuesto presupuesto,
            Map<String, String> modelos,
            String reproduceA) {
    }

    public record EntidadEnCandidato(String rol, String entidadId, String nombre) {
    }

    public record Evidencia(String fragmento, String hashContenido) {
    }

    /** Un posible hecho. {@code proceso} es el identificador del proceso en la fuente (en ARCE, el ocid). */
    public record Candidato(
            String candidatoId,
            String fuente,
            String identificadorEnFuente,
            String proceso,
            String url,
            String fechaPublicacion,
            String tipoDeHecho,
            List<EntidadEnCandidato> entidades,
            String resumen,
            Evidencia evidencia,
            int datosPersonalesEnmascarados) {
    }

    /** Lo emite cada explorador al terminar: así el cierre sabe cuántos candidatos esperar. */
    public record ExploracionTerminada(String fuente, int publicacionesLeidas, int candidatosEmitidos, int sinProcesar) {
    }

    public record CandidatoCotejado(
            String candidatoId,
            String claveDeHecho,
            String estado,
            Long hechoRelacionadoId,
            List<String> diferencias,
            String razon) {
    }

    public record EvidenciaConUrl(String url, String fragmento) {
    }

    public record Prueba(String prueba, String resultado, String detalle) {
    }

    public record CandidatoGraduado(
            String candidatoId,
            String claveDeHecho,
            String afirmacion,
            String grado,
            List<EvidenciaConUrl> evidencia,
            String razonamiento,
            String busqueda,
            boolean fechaConfirmada,
            List<Prueba> pruebas) {
    }

    public record HechoAnalizado(
            String candidatoId,
            String claveDeHecho,
            String grado,
            String queDice,
            String porQueImporta,
            String queMirar) {
    }

    public record Informe(
            String clienteId,
            String fechaObjetivo,
            List<String> hechos,
            boolean sinNovedades,
            int pendientesDeRevision,
            boolean corridaCortada,
            String modo) {
    }

    public record RevisionPendiente(String claveDeHecho, String grado, String motivo) {
    }

    public record FuenteFallida(String fuente, String url, String tipoDeFalla, String detalle) {
    }

    public record UsoModelo(
            String agenteQueLlama,
            String modelo,
            String resultado,
            long tokensEntrada,
            long tokensSalida,
            long tokensCacheLectura,
            long tokensCacheEscritura,
            BigDecimal costoUsd,
            BigDecimal reservaUsd,
            long duracionMs,
            String limite) {
    }

    public record CorridaCortada(String limite, BigDecimal gastadoUsd, int pasos, long minutos, int sinProcesar) {
    }

    public record CorridaTerminada(String estado, Map<String, Integer> conteos, BigDecimal costoUsd, long duracionSegundos) {
    }
}
