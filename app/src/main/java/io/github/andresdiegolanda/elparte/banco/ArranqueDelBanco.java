package io.github.andresdiegolanda.elparte.banco;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Con el perfil {@code banco}, que activa compose.banco.yaml, la aplicación arranca la medición contra
 * el banco apenas levanta (ADR 0014). La corre en un hilo aparte, así la sala de máquinas de la medición
 * se puede mirar mientras avanza, en el puerto 8081.
 */
@Component
@Profile("banco")
public class ArranqueDelBanco implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArranqueDelBanco.class);

    private final MedicionContraElBanco medicion;
    private final String nombre;
    private final String desde;
    private final String hasta;
    private final String entidad;
    private final BigDecimal costoMaximoUsd;
    private final Path carpeta;
    private final String commit;

    public ArranqueDelBanco(MedicionContraElBanco medicion,
            @Value("${elparte.banco.medicion:}") String nombre,
            @Value("${elparte.banco.desde:}") String desde,
            @Value("${elparte.banco.hasta:}") String hasta,
            @Value("${elparte.banco.entidad:organismo:bps}") String entidad,
            @Value("${elparte.banco.costo-maximo-usd:5.00}") BigDecimal costoMaximoUsd,
            @Value("${elparte.banco.carpeta:/mediciones}") Path carpeta,
            @Value("${elparte.banco.commit:}") String commit) {
        this.medicion = medicion;
        this.nombre = nombre;
        this.desde = desde;
        this.hasta = hasta;
        this.entidad = entidad;
        this.costoMaximoUsd = costoMaximoUsd;
        this.carpeta = carpeta;
        this.commit = commit;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (nombre.isBlank() || desde.isBlank() || hasta.isBlank()) {
            log.error("Para medir contra el banco faltan ELPARTE_BANCO_MEDICION, ELPARTE_BANCO_DESDE o ELPARTE_BANCO_HASTA "
                    + "(medición {}, noches del {} al {}); no se mide nada", nombre, desde, hasta);
            return;
        }
        MedicionContraElBanco.Parametros parametros;
        try {
            parametros = new MedicionContraElBanco.Parametros(nombre, LocalDate.parse(desde), LocalDate.parse(hasta),
                    entidad, costoMaximoUsd, carpeta, commit);
        } catch (DateTimeParseException e) {
            log.error("Las noches de la medición van como AAAA-MM-DD, y son {} y {}; no se mide nada", desde, hasta);
            return;
        }
        log.info("Medición contra el banco {}: noches del {} al {}, entidad {}, tope {} USD", parametros.medicion(),
                parametros.desde(), parametros.hasta(), parametros.entidad(), parametros.costoMaximoUsd());
        Thread.ofPlatform().name("medicion-del-banco").start(() -> {
            try {
                medicion.medir(parametros);
            } catch (RuntimeException e) {
                log.error("La medición {} se detuvo: {}", parametros.medicion(), e.getMessage(), e);
            }
        });
    }
}
