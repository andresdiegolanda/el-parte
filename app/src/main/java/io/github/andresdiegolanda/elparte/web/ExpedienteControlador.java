package io.github.andresdiegolanda.elparte.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import io.github.andresdiegolanda.elparte.analista.Analista;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;

/**
 * El expediente de cada entidad (T33, docs/arquitectura.md): todo lo que la base sabe de ella, cada hecho
 * con todas sus versiones, en orden, para consultar cuando se quiera y no solo lo de anoche. Los hechos
 * se ordenan por la noche en que aparecieron, y se ven mes por mes, con un resumen de cada mes arriba.
 * Se ve sin entrar: son hechos de fuentes públicas, ya enmascarados (ADR 0008).
 */
@Controller
public class ExpedienteControlador {

    /** La noche en que apareció cada hecho de la entidad: la más vieja de las corridas de sus versiones. */
    private static final String PRIMERAS = """
            with primeras as (
                select h.id, h.clave_de_hecho, h.tipo_de_hecho, min(c.fecha_objetivo) as noche
                from parte.hechos h
                join parte.hecho_entidades he on he.hecho_id = h.id and he.entidad_id = :entidad
                join parte.versiones_de_hecho v on v.hecho_id = h.id
                join parte.corridas c on c.id = v.corrida_id
                group by h.id, h.clave_de_hecho, h.tipo_de_hecho
            )
            """;

    /** La ventana y el presupuesto de una lectura del expediente (ADR 0018). */
    static final int DIAS_DE_UNA_LECTURA = 90;
    static final BigDecimal COSTO_DE_UNA_LECTURA = new BigDecimal("0.50");

    private final JdbcClient jdbc;
    private final InicioDeCorrida inicio;

    public ExpedienteControlador(JdbcClient jdbc, InicioDeCorrida inicio) {
        this.jdbc = jdbc;
        this.inicio = inicio;
    }

    /** La última lectura del expediente, con sus observaciones; {@code estado} es el de su corrida. */
    public record Lectura(String corridaId, String estado, String limite, String desde, String hasta, Integer hechosLeidos,
            Integer hechosCortados, Integer propuestas, Integer descartadas, String modelo, BigDecimal costo,
            List<Observacion> observaciones) {

        public boolean enCurso() {
            return "en_curso".equals(estado);
        }
    }

    /** Una observación, con los hechos que cita y el mes de cada uno, para enlazarlos. */
    public record Observacion(String clase, String texto, String grado, String razonamiento, List<Cita> citas) {
    }

    public record Cita(String clave, String mes) {
    }

    @PostMapping("/expediente/{entidad}/lectura")
    public String leer(@PathVariable String entidad) {
        jdbc.sql("select id from parte.entidades where id = :id").param("id", entidad).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una entidad " + entidad));
        String corrida = inicio.iniciarLectura(entidad, DIAS_DE_UNA_LECTURA, COSTO_DE_UNA_LECTURA);
        return "redirect:/expediente/" + entidad + "?lectura=" + corrida;
    }

    private Optional<Lectura> ultimaLectura(String entidad) {
        return jdbc.sql("""
                select l.corrida_id, c.estado, c.limite_alcanzado, l.desde::text, l.hasta::text, l.hechos_leidos,
                       l.hechos_cortados, l.propuestas, l.descartadas, c.modelos->>'lector-de-expedientes',
                       coalesce(lc.gastado_usd, 0)
                from parte.lecturas_de_expediente l
                join parte.corridas c on c.id = l.corrida_id
                left join parte.libros_de_corrida lc on lc.corrida_id = l.corrida_id
                where l.entidad_id = :entidad and c.modo = 'lectura'
                order by c.iniciada_en desc limit 1
                """).param("entidad", entidad)
                .query((f, n) -> new Lectura(f.getString(1), f.getString(2), f.getString(3), f.getString(4), f.getString(5),
                        (Integer) f.getObject(6), (Integer) f.getObject(7), (Integer) f.getObject(8), (Integer) f.getObject(9),
                        f.getString(10), f.getBigDecimal(11), new ArrayList<>()))
                .optional()
                .map(l -> {
                    l.observaciones().addAll(observaciones(entidad, l.corridaId()));
                    return l;
                });
    }

    private List<Observacion> observaciones(String entidad, String corridaId) {
        Map<String, String> meses = new LinkedHashMap<>();
        jdbc.sql(PRIMERAS + "select p.clave_de_hecho, to_char(p.noche, 'YYYY-MM') from primeras p").param("entidad", entidad)
                .query((f, n) -> meses.put(f.getString(1), f.getString(2))).list();
        return jdbc.sql("""
                select clase, texto, grado, razonamiento, hechos from parte.observaciones
                where corrida_id = :id order by numero
                """).param("id", corridaId)
                .query((f, n) -> {
                    String[] hechos = (String[]) f.getArray(5).getArray();
                    return new Observacion(f.getString(1), f.getString(2), f.getString(3), f.getString(4),
                            java.util.Arrays.stream(hechos).map(h -> new Cita(h, meses.get(h))).toList());
                })
                .list();
    }

    public record Entidad(String id, String nombre, long hechos, String ultima) {
    }

    public record Mes(String mes, long hechos, long llamados, long adjudicaciones, long otros, long versiones,
            long inferidos) {
    }

    public record Evidencia(String url, String fragmento) {
    }

    public record Version(int numero, String grado, String afirmacion, String razonamiento, String fechaPublicacion,
            String queDice, String porQueImporta, String queMirar, String estadoRevision, String notaDeRevision,
            String corridaId, String noche, String mensajeCandidato, List<Evidencia> evidencias) {

        public boolean conAnalisis() {
            return !porQueImporta.startsWith(Analista.SIN_ANALISIS) || !queMirar.startsWith(Analista.SIN_ANALISIS);
        }
    }

    public record Hecho(String clave, String tipo, String noche, List<Version> versiones) {

        public Version ultima() {
            return versiones.getLast();
        }
    }

    @GetMapping("/expediente")
    public String entidades(Model modelo) {
        modelo.addAttribute("entidades", jdbc.sql("""
                select e.id, e.nombre, count(distinct he.hecho_id),
                       (select max(c.fecha_objetivo)::text from parte.versiones_de_hecho v
                          join parte.corridas c on c.id = v.corrida_id
                          join parte.hecho_entidades x on x.hecho_id = v.hecho_id
                         where x.entidad_id = e.id)
                from parte.entidades e left join parte.hecho_entidades he on he.entidad_id = e.id
                group by e.id, e.nombre
                order by count(distinct he.hecho_id) desc, e.nombre
                """).query((f, n) -> new Entidad(f.getString(1), f.getString(2), f.getLong(3), f.getString(4))).list());
        return "expedientes";
    }

    @GetMapping("/expediente/{entidad}")
    public String expediente(@PathVariable String entidad, @RequestParam(required = false) String mes, Model modelo) {
        String nombre = jdbc.sql("select nombre from parte.entidades where id = :id").param("id", entidad)
                .query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una entidad " + entidad));
        List<Mes> meses = jdbc.sql(PRIMERAS + """
                select to_char(p.noche, 'YYYY-MM'), count(*),
                       count(*) filter (where p.tipo_de_hecho = 'llamado'),
                       count(*) filter (where p.tipo_de_hecho = 'adjudicacion'),
                       count(*) filter (where p.tipo_de_hecho not in ('llamado', 'adjudicacion')),
                       (select count(*) from parte.versiones_de_hecho v where v.hecho_id = any(array_agg(p.id))),
                       (select count(*) from parte.versiones_de_hecho v
                         where v.hecho_id = any(array_agg(p.id)) and v.grado = 'INFERRED')
                from primeras p group by 1 order by 1 desc
                """).param("entidad", entidad)
                .query((f, n) -> new Mes(f.getString(1), f.getLong(2), f.getLong(3), f.getLong(4), f.getLong(5),
                        f.getLong(6), f.getLong(7)))
                .list();
        Optional<String> elegido = meses.stream().map(Mes::mes).filter(m -> m.equals(mes)).findFirst()
                .or(() -> meses.stream().map(Mes::mes).findFirst());
        modelo.addAttribute("entidad", entidad);
        modelo.addAttribute("nombre", nombre);
        modelo.addAttribute("meses", meses);
        modelo.addAttribute("mes", elegido.orElse(null));
        modelo.addAttribute("hechos", elegido.map(m -> hechos(entidad, m)).orElse(List.of()));
        modelo.addAttribute("lectura", ultimaLectura(entidad).orElse(null));
        modelo.addAttribute("diasDeUnaLectura", DIAS_DE_UNA_LECTURA);
        modelo.addAttribute("costoDeUnaLectura", COSTO_DE_UNA_LECTURA);
        return "expediente";
    }

    private List<Hecho> hechos(String entidad, String mes) {
        Map<Long, Hecho> porId = new LinkedHashMap<>();
        jdbc.sql(PRIMERAS + """
                select p.id, p.clave_de_hecho, p.tipo_de_hecho, p.noche::text from primeras p
                where to_char(p.noche, 'YYYY-MM') = :mes order by p.noche desc, p.clave_de_hecho
                """).param("entidad", entidad).param("mes", mes)
                .query((f, n) -> porId.put(f.getLong(1), new Hecho(f.getString(2), f.getString(3), f.getString(4),
                        new ArrayList<>())))
                .list();
        if (porId.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Evidencia>> evidencias = new LinkedHashMap<>();
        jdbc.sql("""
                select e.version_id, e.url, e.fragmento from parte.evidencias e
                join parte.versiones_de_hecho v on v.id = e.version_id
                where v.hecho_id in (:hechos) order by e.version_id, e.id
                """).param("hechos", porId.keySet())
                .query((f, n) -> evidencias.computeIfAbsent(f.getLong(1), k -> new ArrayList<>())
                        .add(new Evidencia(f.getString(2), f.getString(3))))
                .list();
        jdbc.sql("""
                select v.hecho_id, v.id, v.numero, v.grado, v.afirmacion, v.razonamiento, v.fecha_publicacion,
                       v.que_dice, v.por_que_importa, v.que_mirar, v.estado_revision, r.nota, v.corrida_id,
                       c.fecha_objetivo::text, v.mensaje_candidato
                from parte.versiones_de_hecho v
                join parte.corridas c on c.id = v.corrida_id
                left join parte.revisiones r on r.version_id = v.id
                where v.hecho_id in (:hechos) order by v.hecho_id, v.numero
                """).param("hechos", porId.keySet())
                .query((f, n) -> porId.get(f.getLong(1)).versiones().add(new Version(f.getInt(3), f.getString(4),
                        f.getString(5), f.getString(6), f.getString(7), f.getString(8), f.getString(9), f.getString(10),
                        f.getString(11), f.getString(12), f.getString(13), f.getString(14), f.getString(15),
                        evidencias.getOrDefault(f.getLong(2), List.of()))))
                .list();
        return List.copyOf(porId.values());
    }
}
