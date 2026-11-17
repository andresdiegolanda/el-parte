package io.github.andresdiegolanda.elparte.acceso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.BuzonDeAvisos;
import io.github.andresdiegolanda.elparte.BuzonDeAvisos.Aviso;
import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Informe;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * El aviso diario del parte (ADR 0013, T35). Las noches de la prueba son de agosto, en las que ARCE de
 * prueba no publica nada: no llaman al modelo ni cambian lo que ven las otras pruebas.
 */
class AvisoDelParteTest extends ConServicios {

    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    BuzonDeAvisos buzon;
    @Autowired
    AvisoDiario preferencia;
    @Autowired
    @Qualifier(FuenteDeCuentas.NOMBRE)
    DataSource fuenteDeCuentas;

    @Test
    void cadaInvitadoQueLoQuiereRecibeUnAvisoPorNocheSoloDeLasCorridasNormales() {
        // Una noche normal: un aviso para cada invitado, y ninguno para la demostración, que no tiene correo.
        String primera = correrYEsperarElAviso(() -> inicio.iniciarNormal(LocalDate.parse("2026-08-20")));
        List<Aviso> avisos = esperar(() -> buzon.deLaNoche("2026-08-20"), lista -> lista.size() >= 2);
        assertThat(avisos).extracting(Aviso::correo)
                .containsExactlyInAnyOrder(RESPONSABLE, INVITADA.toLowerCase());
        assertThat(avisos).allSatisfy(aviso -> {
            assertThat(aviso.asunto()).isEqualTo("El Parte de la noche del 2026-08-20: sin novedades");
            assertThat(aviso.cuerpo()).contains("no hubo nada nuevo sobre lo que seguís",
                    "/manana?noche=2026-08-20", "/perfil").doesNotContain("arce:");
        });

        // La misma noche otra vez no vuelve a avisar.
        correrYEsperarElAviso(() -> inicio.iniciarNormal(LocalDate.parse("2026-08-20")));
        assertThat(buzon.deLaNoche("2026-08-20")).hasSize(2);

        // Una repetición no avisa, aunque la noche no tenga envíos anotados.
        JdbcClient.create(fuenteDeCuentas).sql("delete from cuentas.envios_del_parte where fecha_objetivo = :n")
                .param("n", LocalDate.parse("2026-08-20")).update();
        correrYEsperarElAviso(() -> inicio.iniciarReproduccion(primera));
        assertThat(buzon.deLaNoche("2026-08-20")).hasSize(2);

        // Quien apagó el aviso en su perfil no lo recibe.
        UUID invitada = JdbcClient.create(fuenteDeCuentas).sql("select cliente_id from cuentas.usuarios where correo = :c")
                .param("c", INVITADA.toLowerCase()).query(UUID.class).single();
        preferencia.cambiar(invitada, false);
        try {
            correrYEsperarElAviso(() -> inicio.iniciarNormal(LocalDate.parse("2026-08-21")));
            assertThat(esperar(() -> buzon.deLaNoche("2026-08-21"), lista -> !lista.isEmpty())).extracting(Aviso::correo)
                    .containsExactly(RESPONSABLE);
        } finally {
            preferencia.cambiar(invitada, true);
        }
    }

    @Test
    void elAvisoDiceCuantosHechosHayCuantosEsperanRevisionYSiLaCorridaSeCorto() {
        Informe informe = new Informe(UUID.randomUUID().toString(), "2026-11-12",
                List.of("arce:a:llamado", "arce:b:adjudicacion", "arce:c:llamado"), false, 1, true, "normal");
        assertThat(AvisoDelParte.asunto(informe)).isEqualTo("El Parte de la noche del 2026-11-12: 3 hechos");
        assertThat(AvisoDelParte.cuerpo(informe, "https://ejemplo.test"))
                .contains("3 hechos sobre lo que seguís, 1 todavía sin revisar.",
                        "La corrida se cortó por su presupuesto", "Leelo en https://ejemplo.test/manana?noche=2026-11-12")
                .doesNotContain("arce:");
        Informe uno = new Informe(UUID.randomUUID().toString(), "2026-11-12", List.of("arce:a:llamado"), false, 0, false, "normal");
        assertThat(AvisoDelParte.asunto(uno)).endsWith(": 1 hecho");
        assertThat(AvisoDelParte.cuerpo(uno, "https://ejemplo.test")).contains("1 hecho sobre lo que seguís.")
                .doesNotContain("sin revisar", "se cortó");
    }

    /** Corre, espera que termine y sus informes, y un poco más, para que el aviso los haya leído. */
    private String correrYEsperarElAviso(Supplier<String> correr) {
        String corrida = correr.get();
        esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", corrida)
                .query(String.class).single(), estado -> !"en_curso".equals(estado));
        // Un informe por cliente que sigue algo: la demostración y los dos invitados de las pruebas.
        esperar(() -> registro.deCorridaYTipo(corrida, Tipos.INFORMES), lista -> lista.size() == 3);
        // El aviso escucha los informes en su propio grupo: lo que dice que no llegó solo puede fallar por
        // pasar de largo, nunca por esperar de más.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return corrida;
    }

    private static <T> T esperar(Supplier<T> valor, Predicate<T> listo) {
        Instant limite = Instant.now().plus(Duration.ofSeconds(120));
        T actual = valor.get();
        while (!listo.test(actual) && Instant.now().isBefore(limite)) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            actual = valor.get();
        }
        return actual;
    }
}
