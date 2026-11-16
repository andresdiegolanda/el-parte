package io.github.andresdiegolanda.elparte.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.andresdiegolanda.elparte.acceso.UsuarioDeElParte;
import io.github.andresdiegolanda.elparte.analista.Analista;

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

    /** Por qué se cortó la corrida y cuántos candidatos quedaron sin procesar (ADR 0005). */
    public record Corte(String limite, int sinProcesar) {

        public String motivo() {
            return switch (limite) {
                case "costo" -> "se acabó su presupuesto de costo";
                case "pasos" -> "llegó a su máximo de llamadas al modelo";
                case "tiempo" -> "se le acabó el tiempo";
                default -> "alcanzó un límite";
            };
        }
    }

    public record Hecho(String clave, String tipo, int version, String afirmacion, String grado, String razonamiento,
            String fechaPublicacion, boolean fechaConfirmada, String queDice, String porQueImporta, String queMirar,
            String estadoRevision, String notaDeRevision, String mensajeCandidato, List<Evidencia> evidencias) {

        /** Si el analista escribió algo: sin análisis, los dos campos empiezan con {@link Analista#SIN_ANALISIS}. */
        public boolean conAnalisis() {
            return !porQueImporta.startsWith(Analista.SIN_ANALISIS) || !queMirar.startsWith(Analista.SIN_ANALISIS);
        }
    }

    /**
     * Con sesión, el parte de quien entró; sin sesión, el de la cuenta de demostración (ADR 0013). Por
     * omisión, el de la noche más reciente; con {@code ?noche=AAAA-MM-DD}, el de esa noche, porque una noche
     * vieja corrida a mano no es la más reciente.
     */
    @GetMapping("/manana")
    public String manana(@AuthenticationPrincipal UsuarioDeElParte usuario,
            @RequestParam(name = "noche", required = false) String noche, Model modelo) {
        UUID cliente = usuario != null ? usuario.clienteId()
                : jdbc.sql("select cliente_id from parte.clientes where es_demo order by alias limit 1")
                        .query(UUID.class).single();
        LocalDate elegida = null;
        try {
            elegida = noche == null || noche.isBlank() ? null : LocalDate.parse(noche);
        } catch (DateTimeParseException e) {
            // Una fecha que no se entiende muestra la noche más reciente.
        }
        Optional<Informe> informe = jdbc.sql("""
                select i.id, i.fecha_objetivo, i.corrida_id, i.sin_novedades, i.corrida_cortada
                from parte.informes i
                where i.cliente_id = :cliente and (cast(:noche as date) is null or i.fecha_objetivo = cast(:noche as date))
                order by i.fecha_objetivo desc, i.creado_en desc limit 1
                """)
                .param("cliente", cliente).param("noche", elegida)
                .query((f, n) -> new Informe(f.getLong(1), f.getString(2), f.getString(3), f.getBoolean(4), f.getBoolean(5)))
                .optional();
        modelo.addAttribute("nocheElegida", elegida == null ? null : elegida.toString());
        // Las noches con parte de este cliente, para elegir otra.
        modelo.addAttribute("noches", jdbc.sql("""
                select distinct fecha_objetivo from parte.informes where cliente_id = :cliente
                order by fecha_objetivo desc limit 30
                """).param("cliente", cliente).query(String.class).list());
        // Si la fuente no publicó nada esa noche ni el día anterior, como los fines de semana, la página lo dice.
        modelo.addAttribute("leidas", informe.map(i -> jdbc.sql("""
                select coalesce(sum((contenido->>'publicacionesLeidas')::int), 0) from parte.mensajes
                where corrida_id = :c and tipo = 'exploracion.terminada'
                """).param("c", i.corridaId()).query(Long.class).single()).orElse(0L));
        modelo.addAttribute("informe", informe.orElse(null));
        modelo.addAttribute("hechos", informe.map(i -> hechos(i.id())).orElse(List.of()));
        modelo.addAttribute("corte", informe.filter(Informe::corridaCortada).flatMap(i -> jdbc.sql("""
                select contenido->>'limite', (contenido->>'sinProcesar')::int from parte.mensajes
                where corrida_id = :c and tipo = 'corrida.cortada'
                """).param("c", i.corridaId()).query((f, n) -> new Corte(f.getString(1), f.getInt(2))).optional())
                .orElse(null));
        // Una fuente que falló no es lo mismo que una noche sin novedades: la página lo dice.
        modelo.addAttribute("fallas", informe.map(i -> jdbc.sql(
                "select count(*) from parte.mensajes where corrida_id = :c and tipo = 'fuente.fallida'")
                .param("c", i.corridaId()).query(Long.class).single()).orElse(0L));
        modelo.addAttribute("seguidas", jdbc.sql("""
                select e.nombre from parte.suscripciones s
                join parte.entidades e on e.id = s.entidad_id
                where s.cliente_id = :cliente order by e.nombre
                """).param("cliente", cliente).query(String.class).list());
        return "manana";
    }

    private List<Hecho> hechos(long informeId) {
        List<Map<String, Object>> filas = jdbc.sql("""
                select h.clave_de_hecho, h.tipo_de_hecho, v.id as version_id, v.numero, v.afirmacion, v.grado,
                       v.razonamiento, v.fecha_publicacion, v.fecha_confirmada, v.que_dice, v.por_que_importa,
                       v.que_mirar, v.estado_revision, r.nota as nota_de_revision,
                       v.mensaje_candidato
                from parte.informe_hechos ih
                join parte.versiones_de_hecho v on v.id = ih.version_id
                join parte.hechos h on h.id = v.hecho_id
                left join parte.revisiones r on r.version_id = v.id
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
                (String) f.get("por_que_importa"),
                (String) f.get("que_mirar"),
                (String) f.get("estado_revision"),
                (String) f.get("nota_de_revision"),
                (String) f.get("mensaje_candidato"),
                jdbc.sql("select url, fragmento from parte.evidencias where version_id = :v order by id")
                        .param("v", f.get("version_id"))
                        .query((e, n) -> new Evidencia(e.getString(1), e.getString(2))).list()))
                .toList();
    }
}
