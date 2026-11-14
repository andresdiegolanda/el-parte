package io.github.andresdiegolanda.elparte.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La página de la mañana de la cuenta de demostración: el último informe, con cada hecho, su grado, su
 * evidencia y un enlace a su traza en la sala de máquinas (docs/base-de-afirmaciones.md, "Cómo salen
 * las vistas"). El login y las cuentas de los invitados llegan con T25.
 */
@Controller
public class MananaControlador {

    private final JdbcClient jdbc;

    public MananaControlador(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Informe(long id, String fecha, String corridaId, boolean sinNovedades, boolean corridaCortada) {
    }

    public record Evidencia(String url, String fragmento) {
    }

    public record Hecho(String clave, String tipo, int version, String afirmacion, String grado, String razonamiento,
            String fechaPublicacion, boolean fechaConfirmada, String queDice, String estadoRevision,
            String mensajeCandidato, List<Evidencia> evidencias) {
    }

    @GetMapping("/manana")
    public String manana(Model modelo) {
        Optional<Informe> informe = jdbc.sql("""
                select i.id, i.fecha_objetivo, i.corrida_id, i.sin_novedades, i.corrida_cortada
                from parte.informes i join parte.clientes c on c.cliente_id = i.cliente_id
                where c.es_demo
                order by i.fecha_objetivo desc, i.creado_en desc limit 1
                """)
                .query((f, n) -> new Informe(f.getLong(1), f.getString(2), f.getString(3), f.getBoolean(4), f.getBoolean(5)))
                .optional();
        modelo.addAttribute("informe", informe.orElse(null));
        modelo.addAttribute("hechos", informe.map(i -> hechos(i.id())).orElse(List.of()));
        // Una fuente que falló no es lo mismo que una noche sin novedades: la página lo dice.
        modelo.addAttribute("fallas", informe.map(i -> jdbc.sql(
                "select count(*) from parte.mensajes where corrida_id = :c and tipo = 'fuente.fallida'")
                .param("c", i.corridaId()).query(Long.class).single()).orElse(0L));
        modelo.addAttribute("seguidas", jdbc.sql("""
                select e.nombre from parte.suscripciones s
                join parte.clientes c on c.cliente_id = s.cliente_id
                join parte.entidades e on e.id = s.entidad_id
                where c.es_demo order by e.nombre
                """).query(String.class).list());
        return "manana";
    }

    private List<Hecho> hechos(long informeId) {
        List<Map<String, Object>> filas = jdbc.sql("""
                select h.clave_de_hecho, h.tipo_de_hecho, v.id as version_id, v.numero, v.afirmacion, v.grado,
                       v.razonamiento, v.fecha_publicacion, v.fecha_confirmada, v.que_dice, v.estado_revision,
                       v.mensaje_candidato
                from parte.informe_hechos ih
                join parte.versiones_de_hecho v on v.id = ih.version_id
                join parte.hechos h on h.id = v.hecho_id
                where ih.informe_id = :informe
                order by v.grado, h.tipo_de_hecho, h.clave_de_hecho, v.numero
                """).param("informe", informeId).query().listOfRows();
        return filas.stream().map(f -> new Hecho(
                (String) f.get("clave_de_hecho"),
                (String) f.get("tipo_de_hecho"),
                ((Number) f.get("numero")).intValue(),
                (String) f.get("afirmacion"),
                (String) f.get("grado"),
                (String) f.get("razonamiento"),
                (String) f.get("fecha_publicacion"),
                (Boolean) f.get("fecha_confirmada"),
                (String) f.get("que_dice"),
                (String) f.get("estado_revision"),
                (String) f.get("mensaje_candidato"),
                jdbc.sql("select url, fragmento from parte.evidencias where version_id = :v order by id")
                        .param("v", f.get("version_id"))
                        .query((e, n) -> new Evidencia(e.getString(1), e.getString(2))).list()))
                .toList();
    }
}
