package io.github.andresdiegolanda.elparte.corrida;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre la noche de ayer a la hora que diga {@code elparte.corrida.cron}, en hora de Uruguay. Viene
 * apagado ("-"): mientras no esté decidido dónde corre la aplicación (T27), las noches se corren a
 * mano desde la sala de máquinas.
 */
@Component
public class NochePorReloj {

    private final InicioDeCorrida inicio;

    public NochePorReloj(InicioDeCorrida inicio) {
        this.inicio = inicio;
    }

    @Scheduled(cron = "${elparte.corrida.cron:-}", zone = "America/Montevideo")
    public void correr() {
        inicio.iniciarNormal(inicio.ayer());
    }
}
