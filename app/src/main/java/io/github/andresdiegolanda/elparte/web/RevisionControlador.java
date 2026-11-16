package io.github.andresdiegolanda.elparte.web;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import io.github.andresdiegolanda.elparte.web.MananaControlador.Evidencia;

/**
 * La cola de revisión humana, en la sala de máquinas (D6, docs/contratos.md, "Umbral de revisión"):
 * las versiones de hecho pendientes de revisión, la más vieja primero, con lo que hace falta para
 * decidir sin salir de la página. Cada revisión es una fila en {@code parte.revisiones} y cambia el
 * estado de la versión. Nada se borra: un hecho rechazado sigue en la página de la mañana, marcado.
 */
@Controller
public class RevisionControlador {

    private static final Set<String> ESTADOS = Set.of("aceptado", "rechazado");
    private static final int REVISADAS_A_LA_VISTA = 20;

    private final JdbcClient jdbc;

    public RevisionControlador(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Pendiente(long versionId, String clave, String tipo, int version, String afirmacion,
            String razonamiento, String fechaPublicacion, String queDice, String porQueImporta, String queMirar,
            String corridaId, String mensajeCandidato, List<Evidencia> evidencias) {
    }

    public record Revisada(long versionId, String clave, String afirmacion, String estado, String nota,
            String revisadaEn) {
    }

    @GetMapping("/revision")
    public String cola(Model modelo) {
        List<Pendiente> pendientes = jdbc.sql("""
                select v.id, h.clave_de_hecho, h.tipo_de_hecho, v.numero, v.afirmacion, v.razonamiento,
                       v.fecha_publicacion, v.que_dice, v.por_que_importa, v.que_mirar, v.corrida_id, v.mensaje_candidato
                from parte.versiones_de_hecho v join parte.hechos h on h.id = v.hecho_id
                where v.estado_revision = 'pendiente'
                order by v.creada_en, v.id
                """)
                .query((f, n) -> new Pendiente(f.getLong(1), f.getString(2), f.getString(3), f.getInt(4),
                        f.getString(5), f.getString(6), f.getString(7), f.getString(8), f.getString(9),
                        f.getString(10), f.getString(11), f.getString(12), evidencias(f.getLong(1))))
                .list();
        modelo.addAttribute("pendientes", pendientes);
        modelo.addAttribute("revisadas", jdbc.sql("""
                select r.version_id, h.clave_de_hecho, v.afirmacion, r.estado, r.nota,
                       to_char(r.revisada_en at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI')
                from parte.revisiones r
                join parte.versiones_de_hecho v on v.id = r.version_id
                join parte.hechos h on h.id = v.hecho_id
                order by r.revisada_en desc, r.version_id desc limit :limite
                """)
                .param("limite", REVISADAS_A_LA_VISTA)
                .query((f, n) -> new Revisada(f.getLong(1), f.getString(2), f.getString(3), f.getString(4),
                        f.getString(5), f.getString(6)))
                .list());
        return "revision";
    }

    /**
     * Revisa una versión pendiente. Una versión se revisa una sola vez: si ya no está pendiente, la
     * revisión se rechaza y no se pisa la anterior. Un rechazo pide una nota, porque la página de la
     * mañana la muestra para explicar por qué.
     */
    @PostMapping("/revision/{versionId}")
    @Transactional
    public String revisar(@PathVariable long versionId, @RequestParam String estado,
            @RequestParam(required = false) String nota) {
        if (!ESTADOS.contains(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de revisión desconocido: " + estado);
        }
        String limpia = nota == null || nota.isBlank() ? null : nota.trim();
        if ("rechazado".equals(estado) && limpia == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un rechazo necesita una nota que diga por qué");
        }
        int cambiadas = jdbc.sql("""
                update parte.versiones_de_hecho set estado_revision = :estado
                where id = :version and estado_revision = 'pendiente'
                """)
                .param("estado", estado)
                .param("version", versionId)
                .update();
        if (cambiadas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La versión " + versionId + " no existe o ya no está pendiente de revisión");
        }
        jdbc.sql("insert into parte.revisiones (version_id, estado, nota) values (:version, :estado, :nota)")
                .param("version", versionId)
                .param("estado", estado)
                .param("nota", limpia)
                .update();
        return "redirect:/revision";
    }

    private List<Evidencia> evidencias(long versionId) {
        return jdbc.sql("select url, fragmento from parte.evidencias where version_id = :v order by id")
                .param("v", versionId)
                .query((e, n) -> new Evidencia(e.getString(1), e.getString(2)))
                .list();
    }
}
