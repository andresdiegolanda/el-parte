package io.github.andresdiegolanda.elparte.refutador;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.lectura.ControlDeObservaciones.HechoLeido;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionGraduada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionPropuesta;
import io.github.andresdiegolanda.elparte.modelo.Modelo;

/**
 * El refutador, cuando lo que tiene que tirar abajo es una observación sobre un expediente y no una
 * publicación (ADR 0018). La fuente son los hechos que la observación cita: el refutador no sabe nada
 * más de la entidad. Quien propone no pone el grado (ADR 0004).
 */
@Component
public class RefutadorDeObservaciones {

    static final String SISTEMA = """
            Sos el refutador de El Parte. Esta vez no recibís una publicación: recibís una observación que otro \
            agente escribió leyendo el expediente de una entidad, y los hechos ya publicados que la observación \
            cita, uno por línea, con su clave, su noche, su grado y su afirmación. Tu trabajo es intentar tirarla abajo.

            Decidí el grado de la observación:
            - PROVEN: lo que dice se lee en los hechos citados, contándolos o comparándolos, sin suponer nada.
            - INFERRED: no está dicho en los hechos citados pero se deduce de ellos. En "razonamiento" explicá de qué se deduce.
            - NOT_MENTIONED: los hechos citados no la sostienen, o dice más de lo que ellos dicen. En "busqueda" decí qué buscaste.

            Reglas:
            - Juzgá solo contra los hechos citados: no sabés nada más de la entidad ni de sus compras.
            - Si la observación califica, recomienda o explica una causa, es NOT_MENTIONED.
            - Donde dice [persona] hay un dato personal tapado: no lo reconstruyas ni lo menciones.
            - Escribí en español rioplatense y sin guiones largos.
            """;
    private static final int TOKENS_DE_SALIDA = 2000;
    private static final List<String> GRADOS = List.of("PROVEN", "INFERRED", "NOT_MENTIONED");

    private final Modelo modelo;

    public RefutadorDeObservaciones(Modelo modelo) {
        this.modelo = modelo;
    }

    public record VeredictoDeObservacion(String grado, String razonamiento, String busqueda) {
    }

    /**
     * La observación graduada, con el grado limitado por los hechos citados. Si el refutador no la
     * sostiene, o el modelo no respondió, el grado es NOT_MENTIONED y no se publica.
     *
     * @throws io.github.andresdiegolanda.elparte.presupuesto.SinPresupuesto si el presupuesto no alcanza
     */
    public ObservacionGraduada graduar(String corridaId, String causaId, ObservacionPropuesta propuesta,
            List<HechoLeido> citados, String gradoMaximo) {
        VeredictoDeObservacion veredicto;
        try {
            veredicto = modelo.pedir(new Modelo.Pedido(corridaId, Refutador.AGENTE, causaId, propuesta.numero(), SISTEMA,
                    pedido(propuesta, citados), TOKENS_DE_SALIDA), VeredictoDeObservacion.class);
        } catch (Modelo.FallaDelModelo falla) {
            veredicto = new VeredictoDeObservacion("NOT_MENTIONED", null,
                    "No hubo veredicto del modelo (" + falla.getMessage() + "); sin veredicto no se publica.");
        }
        String grado = veredicto.grado() == null ? "" : veredicto.grado().trim().toUpperCase();
        String razonamiento = vacio(veredicto.razonamiento()) ? null : veredicto.razonamiento().trim();
        String busqueda = vacio(veredicto.busqueda()) ? null : veredicto.busqueda().trim();
        if (!GRADOS.contains(grado)) {
            busqueda = "El modelo devolvió un grado que no existe (" + veredicto.grado() + "); no se publica.";
            grado = "NOT_MENTIONED";
        }
        if ("INFERRED".equals(grado) && razonamiento == null) {
            busqueda = "El modelo dijo INFERRED sin decir de qué se deduce; sin razonamiento no se publica (ADR 0004).";
            grado = "NOT_MENTIONED";
        }
        String final_ = grado;
        if ("PROVEN".equals(grado) && "INFERRED".equals(gradoMaximo)) {
            // Una observación no vale más que el más débil de sus hechos (ADR 0018).
            final_ = "INFERRED";
            razonamiento = "Cita un hecho deducido, así que la observación queda deducida como mucho.";
        }
        return new ObservacionGraduada(propuesta.numero(), propuesta.clase(), propuesta.texto(), propuesta.hechos(), final_,
                grado, "NOT_MENTIONED".equals(final_) ? null : razonamiento, busqueda);
    }

    static String pedido(ObservacionPropuesta propuesta, List<HechoLeido> citados) {
        StringBuilder texto = new StringBuilder("Observación propuesta (").append(propuesta.clase()).append("):\n")
                .append(propuesta.texto().replace('\n', ' ')).append("\n\nHechos citados:\n");
        for (HechoLeido hecho : citados) {
            texto.append(hecho.clave()).append(" | ").append(hecho.noche()).append(" | ").append(hecho.grado()).append(" | ")
                    .append(hecho.afirmacion().replace('\n', ' ')).append('\n');
        }
        return texto.toString();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
