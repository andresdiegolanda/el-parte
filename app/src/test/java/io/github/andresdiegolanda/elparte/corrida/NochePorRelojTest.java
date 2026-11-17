package io.github.andresdiegolanda.elparte.corrida;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.ConServicios;

/**
 * La corrida por reloj se pone al día (T43): corre la noche de ayer apenas pasa la hora, si no la
 * tiene, y no la corre dos veces. Las noches de la prueba son de agosto, en las que ARCE de prueba no
 * publica nada, así no llaman al modelo ni cambian lo que ven las otras pruebas.
 */
class NochePorRelojTest extends ConServicios {

    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    JdbcClient jdbc;

    private static Clock a(String horaDeUruguay) {
        return Clock.fixed(ZonedDateTime.parse(horaDeUruguay + "-03:00[America/Montevideo]").toInstant(),
                InicioDeCorrida.URUGUAY);
    }

    @Test
    void correLaNocheDeAyerApenasPasaLaHoraYNoLaRepite() {
        NochePorReloj reloj = new NochePorReloj(inicio, Clock.systemUTC(), "06:00");

        // Antes de la hora no corre nada.
        assertThat(reloj.revisar(a("2026-08-02T05:55:00"))).isEmpty();
        assertThat(corridasNormales("2026-08-01")).isZero();

        // La máquina estuvo apagada a las 6: la primera revisión después corre la noche de ayer.
        Optional<String> corrida = reloj.revisar(a("2026-08-02T07:40:00"));
        assertThat(corrida).contains("c-2026-08-01-normal");
        assertThat(esperarFin(corrida.get())).isEqualTo("completa");

        // La revisión siguiente ya la encuentra, y al otro día a la misma hora tampoco vuelve atrás.
        assertThat(reloj.revisar(a("2026-08-02T07:45:00"))).isEmpty();
        assertThat(reloj.revisar(a("2026-08-02T23:59:00"))).isEmpty();
        assertThat(corridasNormales("2026-08-01")).isEqualTo(1);

        // Apagada no corre nunca.
        assertThat(new NochePorReloj(inicio, Clock.systemUTC(), "-").revisar(a("2026-08-03T07:00:00"))).isEmpty();
        assertThat(corridasNormales("2026-08-02")).isZero();
    }

    @Test
    void siLaRevisionYLaSalaCoincidenLaNocheCorreUnaSolaVez() throws Exception {
        // Una noche corrida a mano antes cuenta: el reloj no la repite.
        String aMano = inicio.iniciarNormal(LocalDate.parse("2026-08-10"));
        assertThat(new NochePorReloj(inicio, Clock.systemUTC(), "06:00").revisar(a("2026-08-11T06:01:00"))).isEmpty();
        esperarFin(aMano);

        // Cuatro revisiones a la vez sobre la misma noche: inicia una sola.
        CountDownLatch largada = new CountDownLatch(1);
        List<CompletableFuture<Optional<String>>> revisiones = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            revisiones.add(CompletableFuture.supplyAsync(() -> {
                try {
                    largada.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new NochePorReloj(inicio, Clock.systemUTC(), "06:00").revisar(a("2026-08-12T06:05:00"));
            }));
        }
        largada.countDown();
        List<String> iniciadas = new ArrayList<>();
        for (CompletableFuture<Optional<String>> r : revisiones) {
            r.get().ifPresent(iniciadas::add);
        }
        assertThat(iniciadas).containsExactly("c-2026-08-11-normal");
        assertThat(corridasNormales("2026-08-11")).isEqualTo(1);
        esperarFin(iniciadas.get(0));
    }

    private long corridasNormales(String noche) {
        return jdbc.sql("select count(*) from parte.corridas where modo = 'normal' and fecha_objetivo = :noche")
                .param("noche", LocalDate.parse(noche)).query(Long.class).single();
    }

    private String esperarFin(String corridaId) {
        Instant limite = Instant.now().plus(Duration.ofSeconds(120));
        String estado = estado(corridaId);
        while ("en_curso".equals(estado) && Instant.now().isBefore(limite)) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            estado = estado(corridaId);
        }
        return estado;
    }

    private String estado(String corridaId) {
        return jdbc.sql("select estado from parte.corridas where id = :id").param("id", corridaId)
                .query(String.class).single();
    }
}
