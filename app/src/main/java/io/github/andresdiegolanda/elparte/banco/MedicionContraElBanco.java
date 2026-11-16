package io.github.andresdiegolanda.elparte.banco;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;

/**
 * La medición contra el banco de pruebas (ADR 0014): una corrida en modo banco por cada noche, en orden y
 * una por vez, sobre la base de esta medición, y al final la salida de El Parte en la carpeta de la
 * medición (banco-de-pruebas/formato.md, "La salida de El Parte").
 *
 * <p>Corre en un Docker Compose aparte, así que todas las corridas del banco de esta base son de la misma
 * medición. Si se interrumpe, al volver a medir saltea las noches que ya terminaron y espera la que
 * quedó en curso. Antes de iniciar cada noche suma lo gastado en el modelo por todas las corridas del
 * banco, y si llegó al tope no inicia más.
 *
 * <p>Si una noche tuvo llamadas al modelo con error, la medición se detiene ahí y no escribe la salida:
 * el refutador gradúa NOT_MENTIONED lo que no pudo verificar, así que esa noche se ve como una noche sin
 * novedades y la medición daría mal. Pasó en la medición 2026-11-16-1, cuando se acabó el crédito de la
 * API. Esa noche no se puede volver a correr en la misma base: se corrige la causa y se mide de nuevo,
 * con otro nombre.
 */
@Service
public class MedicionContraElBanco {

    private static final Logger log = LoggerFactory.getLogger(MedicionContraElBanco.class);
    private static final Pattern MEDICION = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d+");

    private final InicioDeCorrida inicio;
    private final SalidaDelBanco salida;
    private final JdbcClient jdbc;
    private final long esperaMs;
    private final Duration maximoPorNoche;

    public MedicionContraElBanco(InicioDeCorrida inicio, SalidaDelBanco salida, JdbcClient jdbc,
            @Value("${elparte.banco.espera-ms:2000}") long esperaMs,
            @Value("${elparte.corrida.minutos-maximos:45}") int minutosMaximos) {
        this.inicio = inicio;
        this.salida = salida;
        this.jdbc = jdbc;
        this.esperaMs = esperaMs;
        // El cierre corta una corrida a los minutos máximos; esto es por si el cierre no la corta.
        this.maximoPorNoche = Duration.ofMinutes(minutosMaximos + 10L);
    }

    /** Qué medir: la medición, sus noches, la entidad, el tope de gasto, dónde dejar la salida y el commit. */
    public record Parametros(String medicion, LocalDate desde, LocalDate hasta, String entidad,
            BigDecimal costoMaximoUsd, Path carpeta, String commit) {
    }

    /**
     * Lo que pasó: las noches corridas ahora, las que ya estaban, si cortó el tope, la noche con errores del
     * modelo si la hubo, y dónde quedó la salida, que es nula si la medición se detuvo por errores.
     */
    public record Resultado(List<LocalDate> corridas, List<LocalDate> salteadas, boolean cortadaPorTope,
            LocalDate nocheConErrores, BigDecimal gastadoUsd, Path salida) {
    }

    public Resultado medir(Parametros p) {
        validar(p);
        List<LocalDate> corridas = new ArrayList<>();
        List<LocalDate> salteadas = new ArrayList<>();
        boolean cortadaPorTope = false;
        for (LocalDate noche = p.desde(); !noche.isAfter(p.hasta()); noche = noche.plusDays(1)) {
            if (terminada(noche).isPresent()) {
                salteadas.add(noche);
                continue;
            }
            String corridaId = enCurso(noche).orElse(null);
            if (corridaId == null) {
                BigDecimal gastado = gastado();
                if (gastado.compareTo(p.costoMaximoUsd()) >= 0) {
                    log.warn("Medición {}: se gastaron {} USD y el tope es {}; no se inicia la noche {} ni las siguientes",
                            p.medicion(), gastado, p.costoMaximoUsd(), noche);
                    cortadaPorTope = true;
                    break;
                }
                corridaId = inicio.iniciarBanco(noche, p.entidad());
            }
            String estado = esperarFin(corridaId);
            log.info("Medición {}: noche {} {} ({})", p.medicion(), noche, estado, corridaId);
            corridas.add(noche);
            Optional<String> error = primerErrorDelModelo(corridaId);
            if (error.isPresent()) {
                BigDecimal gastado = gastado();
                log.error("Medición {}: la noche {} tuvo llamadas al modelo con error, y lo que no se pudo verificar salió "
                        + "NOT_MENTIONED. La medición se detiene sin escribir la salida ({} USD gastados). Primer error: {}. "
                        + "Corregí la causa y medí de nuevo con otro nombre.", p.medicion(), noche, gastado, error.get());
                return new Resultado(corridas, salteadas, false, noche, gastado, null);
            }
        }
        Path archivo = p.carpeta().resolve(p.medicion()).resolve(SalidaDelBanco.ARCHIVO);
        salida.escribir(archivo, p.entidad(), p.commit());
        BigDecimal gastado = gastado();
        log.info("Medición {}: {} noches corridas ahora, {} ya estaban, {} USD en total{}; salida en {}", p.medicion(),
                corridas.size(), salteadas.size(), gastado, cortadaPorTope ? ", cortada por el tope" : "", archivo);
        return new Resultado(corridas, salteadas, cortadaPorTope, null, gastado, archivo);
    }

    private void validar(Parametros p) {
        if (p.medicion() == null || !MEDICION.matcher(p.medicion()).matches()) {
            throw new IllegalArgumentException("La medición se nombra AAAA-MM-DD-N, y es " + p.medicion());
        }
        if (p.desde() == null || p.hasta() == null || p.hasta().isBefore(p.desde())) {
            throw new IllegalArgumentException("Las noches van de una fecha a otra igual o posterior: " + p.desde() + " a " + p.hasta());
        }
        if (p.costoMaximoUsd() == null || p.costoMaximoUsd().signum() <= 0) {
            throw new IllegalArgumentException("El tope de gasto tiene que ser mayor que cero");
        }
        boolean existe = jdbc.sql("select count(*) from parte.entidades where id = :id").param("id", p.entidad())
                .query(Long.class).single() == 1;
        if (!existe) {
            throw new IllegalArgumentException("La entidad " + p.entidad() + " no está en parte.entidades");
        }
    }

    private Optional<String> terminada(LocalDate noche) {
        return jdbc.sql("""
                select id from parte.corridas
                where modo = 'banco' and fecha_objetivo = :noche and estado <> 'en_curso'
                order by iniciada_en limit 1
                """).param("noche", noche).query(String.class).optional();
    }

    private Optional<String> enCurso(LocalDate noche) {
        return jdbc.sql("""
                select id from parte.corridas
                where modo = 'banco' and fecha_objetivo = :noche and estado = 'en_curso'
                order by iniciada_en limit 1
                """).param("noche", noche).query(String.class).optional();
    }

    /**
     * El primer error del modelo de una corrida, si lo hubo. Se lee de las grabaciones, que el agente
     * escribe en su transacción antes de seguir, y no de {@code uso.modelo}, que llega a la base por el
     * observador y puede llegar después del cierre.
     */
    private Optional<String> primerErrorDelModelo(String corridaId) {
        return jdbc.sql("""
                select contenido->>'error' from parte.grabaciones
                where corrida_id = :corrida and tipo = 'modelo' and contenido->>'error' is not null
                order by grabada_en limit 1
                """).param("corrida", corridaId).query(String.class).optional();
    }

    /** Lo gastado en el modelo por todas las corridas del banco de esta base. */
    private BigDecimal gastado() {
        return jdbc.sql("""
                select coalesce(sum((m.contenido->>'costoUsd')::numeric), 0)
                from parte.mensajes m join parte.corridas c on c.id = m.corrida_id
                where c.modo = 'banco' and m.tipo = 'uso.modelo'
                """).query(BigDecimal.class).single();
    }

    private String esperarFin(String corridaId) {
        Instant limite = Instant.now().plus(maximoPorNoche);
        while (true) {
            String estado = jdbc.sql("select estado from parte.corridas where id = :id").param("id", corridaId)
                    .query(String.class).single();
            if (!"en_curso".equals(estado)) {
                return estado;
            }
            if (Instant.now().isAfter(limite)) {
                throw new IllegalStateException("La corrida " + corridaId + " sigue en curso después de " + maximoPorNoche
                        + "; la medición se detiene");
            }
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("La medición se interrumpió esperando " + corridaId, e);
            }
        }
    }
}
