package io.github.andresdiegolanda.elparte.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaIniciada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * La sala de máquinas: las corridas, y por cada una, qué hizo cada agente con cada candidato, cuánto
 * costó y por qué terminó como terminó (ADR 0002). Cada candidato tiene un ancla con el identificador
 * de su mensaje, que es a donde lleva cada hecho de la página de la mañana.
 */
@Controller
public class SalaDeMaquinasControlador {

    private final JdbcClient jdbc;
    private final RegistroDeMensajes registro;
    private final InicioDeCorrida inicio;
    private final ComparacionDeCorridas comparacion;
    private final JsonMapper json;

    public SalaDeMaquinasControlador(JdbcClient jdbc, RegistroDeMensajes registro, InicioDeCorrida inicio,
            ComparacionDeCorridas comparacion, JsonMapper json) {
        this.jdbc = jdbc;
        this.registro = registro;
        this.inicio = inicio;
        this.comparacion = comparacion;
        this.json = json.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
    }

    public record Corrida(String id, String modo, String fecha, String estado, String limite, String iniciada,
            String terminada, long mensajes, BigDecimal costo) {
    }

    public record Paso(String tipo, String agente, String mensajeId, String emitidoEn, String contenido) {
    }

    public record Cadena(String candidatoId, String mensajeId, String resumen, String desenlace, List<Paso> pasos) {
    }

    @GetMapping("/corridas")
    public String corridas(Model modelo) {
        modelo.addAttribute("corridas", jdbc.sql("""
                select c.id, c.modo, c.fecha_objetivo, c.estado, c.limite_alcanzado,
                       to_char(c.iniciada_en at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI') as iniciada,
                       to_char(c.terminada_en at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI') as terminada,
                       (select count(*) from parte.mensajes m where m.corrida_id = c.id) as mensajes,
                       (select coalesce(sum((m.contenido->>'costoUsd')::numeric), 0) from parte.mensajes m
                         where m.corrida_id = c.id and m.tipo = 'uso.modelo') as costo
                from parte.corridas c order by c.iniciada_en desc limit 50
                """)
                .query((f, n) -> new Corrida(f.getString(1), f.getString(2), f.getString(3), f.getString(4),
                        f.getString(5), f.getString(6), f.getString(7), f.getLong(8), f.getBigDecimal(9)))
                .list());
        modelo.addAttribute("ayer", inicio.ayer().toString());
        return "corridas";
    }

    @PostMapping("/corridas")
    public String iniciar(@RequestParam(defaultValue = "normal") String modo,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String original) {
        String id = switch (modo) {
            case "normal" -> inicio.iniciarNormal(fecha == null || fecha.isBlank() ? inicio.ayer() : LocalDate.parse(fecha));
            case "reproduccion" -> {
                if (original == null || original.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la corrida que se repite");
                }
                yield inicio.iniciarReproduccion(original);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo desconocido: " + modo);
        };
        return "redirect:/corridas/" + id;
    }

    @GetMapping("/corridas/{id}")
    public String corrida(@PathVariable String id, Model modelo) {
        List<Sobre> mensajes = registro.deCorrida(id);
        if (mensajes.isEmpty() && jdbc.sql("select count(*) from parte.corridas where id = :id").param("id", id)
                .query(Integer.class).single() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la corrida " + id);
        }
        Map<String, List<Sobre>> porCausa = new HashMap<>();
        mensajes.forEach(s -> porCausa.computeIfAbsent(s.causaId(), k -> new ArrayList<>()).add(s));

        List<Cadena> cadenas = new ArrayList<>();
        Map<String, BigDecimal> costoPorAgente = new TreeMap<>();
        Map<String, Integer> llamadasPorAgente = new TreeMap<>();
        List<Paso> otros = new ArrayList<>();
        CorridaIniciada iniciada = null;
        for (Sobre sobre : mensajes) {
            switch (sobre.tipo()) {
                case Tipos.CORRIDA_INICIADA -> {
                    iniciada = registro.contenido(sobre, CorridaIniciada.class);
                    otros.add(paso(sobre));
                }
                case Tipos.CANDIDATOS -> cadenas.add(cadena(sobre, porCausa));
                case Tipos.USO_MODELO -> {
                    UsoModelo uso = registro.contenido(sobre, UsoModelo.class);
                    costoPorAgente.merge(uso.agenteQueLlama(), uso.costoUsd(), BigDecimal::add);
                    llamadasPorAgente.merge(uso.agenteQueLlama(), 1, Integer::sum);
                }
                case Tipos.CANDIDATOS_COTEJADOS, Tipos.CANDIDATOS_GRADUADOS, Tipos.HECHOS_ANALIZADOS,
                        Tipos.REVISION_PENDIENTE -> {
                }
                default -> otros.add(paso(sobre));
            }
        }
        modelo.addAttribute("corrida", jdbc.sql("""
                select c.id, c.modo, c.fecha_objetivo, c.estado, c.limite_alcanzado,
                       to_char(c.iniciada_en at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI:SS') as iniciada,
                       to_char(c.terminada_en at time zone 'America/Montevideo', 'YYYY-MM-DD HH24:MI:SS') as terminada,
                       0, 0
                from parte.corridas c where c.id = :id
                """).param("id", id)
                .query((f, n) -> new Corrida(f.getString(1), f.getString(2), f.getString(3), f.getString(4),
                        f.getString(5), f.getString(6), f.getString(7), mensajes.size(), BigDecimal.ZERO))
                .single());
        modelo.addAttribute("iniciada", iniciada);
        modelo.addAttribute("cadenas", cadenas);
        modelo.addAttribute("otros", otros);
        modelo.addAttribute("costoPorAgente", costoPorAgente);
        modelo.addAttribute("llamadasPorAgente", llamadasPorAgente);
        modelo.addAttribute("costoTotal", costoPorAgente.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        if (iniciada != null && iniciada.reproduceA() != null) {
            modelo.addAttribute("comparacion", comparacion.comparar(iniciada.reproduceA(), id));
        }
        return "sala";
    }

    /** Un candidato y lo que le pasó: cotejo, graduación, análisis y revisión, con las llamadas al modelo. */
    private Cadena cadena(Sobre candidato, Map<String, List<Sobre>> porCausa) {
        List<Paso> pasos = new ArrayList<>();
        pasos.add(paso(candidato));
        String finalDelCandidato = "sin terminar";
        Sobre actual = candidato;
        for (String siguiente : List.of(Tipos.CANDIDATOS_COTEJADOS, Tipos.CANDIDATOS_GRADUADOS, Tipos.HECHOS_ANALIZADOS)) {
            Sobre encontrado = null;
            for (Sobre hijo : porCausa.getOrDefault(actual.mensajeId(), List.of())) {
                if (hijo.tipo().equals(Tipos.USO_MODELO) || hijo.tipo().equals(Tipos.REVISION_PENDIENTE)) {
                    pasos.add(paso(hijo));
                } else if (hijo.tipo().equals(siguiente)) {
                    encontrado = hijo;
                }
            }
            if (encontrado == null) {
                break;
            }
            pasos.add(paso(encontrado));
            actual = encontrado;
            finalDelCandidato = switch (siguiente) {
                case Tipos.CANDIDATOS_COTEJADOS -> encontrado.contenido().path("estado").asString();
                case Tipos.CANDIDATOS_GRADUADOS -> encontrado.contenido().path("grado").asString();
                default -> "publicado (" + encontrado.contenido().path("grado").asString() + ")";
            };
        }
        for (Sobre hijo : porCausa.getOrDefault(actual.mensajeId(), List.of())) {
            if (hijo.tipo().equals(Tipos.REVISION_PENDIENTE)) {
                pasos.add(paso(hijo));
            }
        }
        return new Cadena(candidato.contenido().path("candidatoId").asString(), candidato.mensajeId(),
                candidato.contenido().path("resumen").asString(), finalDelCandidato, pasos);
    }

    private Paso paso(Sobre sobre) {
        return new Paso(sobre.tipo(), sobre.agente(), sobre.mensajeId(), sobre.emitidoEn().toString(),
                json.writeValueAsString(sobre.contenido()));
    }
}
