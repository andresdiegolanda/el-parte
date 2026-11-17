package io.github.andresdiegolanda.elparte.web;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;

/**
 * Los modelos de cada agente y sus precios, en la sala de máquinas (ADR 0003, ADR 0005, T29). Los dos
 * viven en la base, con historia: un cambio es una fila nueva, y vale desde la corrida siguiente, que
 * congela los modelos en {@code corrida.iniciada} y usa el precio que regía al empezar. Mirarlos es
 * libre; cambiarlos es del responsable. No se puede elegir un modelo sin precio, porque el presupuesto
 * no sabría cuánto reservar.
 */
@Controller
public class ModelosControlador {

    /** Un nombre de modelo como los de la API: letras, números, puntos y guiones. */
    private static final Pattern NOMBRE = Pattern.compile("[a-z0-9][a-z0-9.-]{2,63}");

    private final JdbcClient jdbc;
    private final Clock reloj;

    public ModelosControlador(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    public record Vigente(String agente, String modelo, String desde) {
    }

    public record Precio(String modelo, BigDecimal entrada, BigDecimal salida, BigDecimal cacheLectura,
            BigDecimal cacheEscritura, String fuente, String consultadoEl, String desde) {
    }

    @GetMapping("/modelos")
    public String modelos(Model modelo) {
        modelo.addAttribute("vigentes", jdbc.sql("""
                select distinct on (agente) agente, modelo,
                       to_char(desde at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI')
                from parte.modelos_de_agente order by agente, desde desc
                """).query((f, n) -> new Vigente(f.getString(1), f.getString(2), f.getString(3))).list());
        modelo.addAttribute("historia", jdbc.sql("""
                select agente, modelo, to_char(desde at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI')
                from parte.modelos_de_agente order by desde desc, agente limit 30
                """).query((f, n) -> new Vigente(f.getString(1), f.getString(2), f.getString(3))).list());
        modelo.addAttribute("precios", jdbc.sql("""
                select modelo, usd_millon_entrada, usd_millon_salida, usd_millon_cache_lectura,
                       usd_millon_cache_escritura, fuente_url, to_char(consultado_el, 'YYYY-MM-DD'),
                       to_char(desde at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI')
                from parte.precios_de_modelo order by modelo, desde desc
                """).query((f, n) -> new Precio(f.getString(1), f.getBigDecimal(2), f.getBigDecimal(3),
                        f.getBigDecimal(4), f.getBigDecimal(5), f.getString(6), f.getString(7), f.getString(8))).list());
        modelo.addAttribute("conPrecio", conPrecio());
        modelo.addAttribute("hoy", LocalDate.now(reloj.withZone(InicioDeCorrida.URUGUAY)).toString());
        return "modelos";
    }

    /** Cambia el modelo de un agente: una fila nueva, que vale desde la corrida siguiente. */
    @PostMapping("/modelos/agente")
    public String cambiarModelo(@RequestParam String agente, @RequestParam String modelo, RedirectAttributes aviso) {
        boolean existe = jdbc.sql("select count(*) from parte.modelos_de_agente where agente = :a").param("a", agente)
                .query(Long.class).single() > 0;
        if (!existe) {
            aviso.addFlashAttribute("error", "No hay un agente " + agente + ".");
        } else if (!conPrecio().contains(modelo)) {
            aviso.addFlashAttribute("error", "El modelo " + modelo + " no tiene precio: cargalo primero, así el presupuesto sabe cuánto reservar.");
        } else {
            jdbc.sql("insert into parte.modelos_de_agente (agente, modelo) values (:a, :m)")
                    .param("a", agente).param("m", modelo).update();
            aviso.addFlashAttribute("hecho", "El " + agente + " usa " + modelo + " desde la corrida siguiente.");
        }
        return "redirect:/modelos";
    }

    /** Carga el precio de un modelo, con su fuente: una fila nueva, que rige desde la corrida siguiente. */
    @PostMapping("/modelos/precio")
    public String cargarPrecio(@RequestParam String modelo, @RequestParam String entrada, @RequestParam String salida,
            @RequestParam String cacheLectura, @RequestParam String cacheEscritura, @RequestParam String fuente,
            @RequestParam String consultadoEl, RedirectAttributes aviso) {
        String error = null;
        BigDecimal[] usd = new BigDecimal[4];
        String[] textos = {entrada, salida, cacheLectura, cacheEscritura};
        for (int i = 0; i < 4 && error == null; i++) {
            try {
                usd[i] = new BigDecimal(textos[i].trim().replace(',', '.'));
                if (usd[i].signum() < 0 || usd[i].compareTo(BigDecimal.valueOf(1000)) > 0) {
                    error = "Cada precio va entre 0 y 1000 dólares por millón de tokens.";
                }
            } catch (NumberFormatException e) {
                error = "Cada precio es un número, en dólares por millón de tokens.";
            }
        }
        LocalDate consultado = null;
        if (error == null && !NOMBRE.matcher(modelo).matches()) {
            error = "El nombre del modelo va como en la API, por ejemplo claude-sonnet-5-5.";
        } else if (error == null && (usd[0].signum() == 0 || usd[1].signum() == 0)) {
            error = "La entrada y la salida no pueden ser gratis: el presupuesto reservaría cero.";
        } else if (error == null && !fuente.startsWith("https://")) {
            error = "La fuente es la página de precios, con https://.";
        } else if (error == null) {
            try {
                consultado = LocalDate.parse(consultadoEl);
                if (consultado.isAfter(LocalDate.now(reloj.withZone(InicioDeCorrida.URUGUAY)))) {
                    error = "La fecha de consulta no puede ser futura.";
                }
            } catch (RuntimeException e) {
                error = "La fecha de consulta va como AAAA-MM-DD.";
            }
        }
        if (error != null) {
            aviso.addFlashAttribute("error", error);
            return "redirect:/modelos";
        }
        jdbc.sql("""
                insert into parte.precios_de_modelo (modelo, usd_millon_entrada, usd_millon_salida,
                    usd_millon_cache_lectura, usd_millon_cache_escritura, fuente_url, consultado_el)
                values (:m, :e, :s, :cl, :ce, :f, :c)
                """).param("m", modelo).param("e", usd[0]).param("s", usd[1]).param("cl", usd[2]).param("ce", usd[3])
                .param("f", fuente).param("c", consultado).update();
        aviso.addFlashAttribute("hecho", "El precio de " + modelo + " rige desde la corrida siguiente.");
        return "redirect:/modelos";
    }

    private List<String> conPrecio() {
        return jdbc.sql("select distinct modelo from parte.precios_de_modelo order by modelo").query(String.class).list();
    }
}
