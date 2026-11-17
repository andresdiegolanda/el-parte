package io.github.andresdiegolanda.elparte.corrida;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre la noche de ayer cuando pasa la hora de {@code elparte.corrida.hora}, en hora de Uruguay (T43).
 * Viene apagado ("-"): la aplicación publicada lo prende a las 6 (compose.publicado.yaml); en local y en
 * las pruebas las noches se corren a mano desde la sala de máquinas.
 *
 * <p>No es una tarea a una hora fija. Cada {@code elparte.corrida.revisar-cada-ms} mira el reloj de pared
 * y, si ya pasó la hora y la noche de ayer no tiene corrida normal, la corre. Así se pone al día: si la
 * máquina estuvo apagada o dormida a esa hora, corre apenas vuelve; si la máquina virtual estuvo en pausa,
 * se atrasa a lo sumo una revisión, y no lo que duró la pausa, porque la hora se mira en el reloj de pared
 * y no en el monótono. Solo la noche de ayer: una más vieja que no corrió se corre a mano.
 */
@Component
public class NochePorReloj {

    private static final Logger log = LoggerFactory.getLogger(NochePorReloj.class);

    private final InicioDeCorrida inicio;
    private final Clock reloj;
    private final Optional<LocalTime> hora;

    public NochePorReloj(InicioDeCorrida inicio, Clock reloj, @Value("${elparte.corrida.hora:-}") String hora) {
        this.inicio = inicio;
        this.reloj = reloj;
        this.hora = hora == null || hora.isBlank() || "-".equals(hora.trim()) ? Optional.empty()
                : Optional.of(LocalTime.parse(hora.trim()));
    }

    @Scheduled(initialDelayString = "${elparte.corrida.revisar-cada-ms:300000}",
            fixedDelayString = "${elparte.corrida.revisar-cada-ms:300000}")
    public void revisarAhora() {
        revisar(reloj);
    }

    /** Lo que hace cada revisión, con el reloj que se le pase. La corrida que inició, si inició una. */
    public Optional<String> revisar(Clock ahora) {
        if (hora.isEmpty()) {
            return Optional.empty();
        }
        ZonedDateTime uruguay = ZonedDateTime.now(ahora.withZone(InicioDeCorrida.URUGUAY));
        if (uruguay.toLocalTime().isBefore(hora.get())) {
            return Optional.empty();
        }
        LocalDate noche = uruguay.toLocalDate().minusDays(1);
        Optional<String> corrida = inicio.iniciarNormalSiFalta(noche);
        corrida.ifPresent(id -> log.info("La noche {} no tenía corrida normal a las {}: se inició {}", noche,
                uruguay.toLocalTime().withNano(0), id));
        return corrida;
    }
}
