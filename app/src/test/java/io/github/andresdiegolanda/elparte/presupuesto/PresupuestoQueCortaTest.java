package io.github.andresdiegolanda.elparte.presupuesto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.ModeloDePrueba;
import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.fuentes.arce.ArceDePrueba;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaCortada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Presupuesto;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import io.github.andresdiegolanda.elparte.modelo.Modelo;

/** El presupuesto que corta (ADR 0005): el libro de la corrida y una noche que se queda sin plata. */
class PresupuestoQueCortaTest extends ConServicios {

    @Autowired
    LibroDeCorrida libro;
    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    ModeloDePrueba modelo;
    @Autowired
    JdbcClient jdbc;

    @Test
    void elLibroNoDejaPasarElTopeNiConReservasALaVez() throws Exception {
        String corrida = corridaSuelta("c-prueba-libro-1");
        Presupuesto presupuesto = new Presupuesto(new BigDecimal("1.00"), 200, 45);

        // Veinte reservas de 0,10 USD a la vez contra un tope de 1 USD: entran diez, ni una más.
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        List<Future<Optional<String>>> reservas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Callable<Optional<String>> reserva = () -> libro.reservar(corrida, presupuesto, new BigDecimal("0.10"));
            reservas.add(hilos.submit(reserva));
        }
        int hechas = 0;
        for (Future<Optional<String>> reserva : reservas) {
            Optional<String> limite = reserva.get();
            if (limite.isEmpty()) {
                hechas++;
            } else {
                assertThat(limite).contains("costo");
            }
        }
        hilos.shutdown();
        assertThat(hechas).isEqualTo(10);
        assertThat(libro("reservado_usd", corrida)).isEqualByComparingTo("1.00");

        // Al registrar lo real se libera la reserva, pero el libro ya está agotado: no entra nada más.
        libro.registrar(corrida, new BigDecimal("0.10"), new BigDecimal("0.03"));
        assertThat(libro("reservado_usd", corrida)).isEqualByComparingTo("0.90");
        assertThat(libro("gastado_usd", corrida)).isEqualByComparingTo("0.03");
        assertThat(libro.reservar(corrida, presupuesto, new BigDecimal("0.01"))).contains("costo");

        // El tope de pasos.
        String otra = corridaSuelta("c-prueba-libro-2");
        Presupuesto dosPasos = new Presupuesto(new BigDecimal("1.00"), 2, 45);
        assertThat(libro.reservar(otra, dosPasos, new BigDecimal("0.01"))).isEmpty();
        assertThat(libro.reservar(otra, dosPasos, new BigDecimal("0.01"))).isEmpty();
        assertThat(libro.reservar(otra, dosPasos, new BigDecimal("0.01"))).contains("pasos");

        // Una corrida que ya no está en curso no gasta más.
        String cortada = corridaSuelta("c-prueba-libro-3");
        jdbc.sql("update parte.corridas set estado = 'cortada', limite_alcanzado = 'tiempo' where id = :id")
                .param("id", cortada).update();
        assertThat(libro.reservar(cortada, presupuesto, new BigDecimal("0.01"))).contains("tiempo");

        jdbc.sql("update parte.corridas set estado = 'fallida' where id in ('c-prueba-libro-1', 'c-prueba-libro-2')").update();
    }

    @Test
    void unaNocheSinPlataSeCortaSinPublicarNadaAMedioVerificarYSeRepiteIgual() {
        // Tres llamados del BPS y un presupuesto que alcanza para una o dos llamadas al modelo: cada
        // llamada de prueba cuesta 0,004 USD, pero el refutador reserva el peor caso, unos 0,014.
        int llamadasAntes = modelo.llamadas();
        String corrida = inicio.iniciarNormal(LocalDate.parse(ArceDePrueba.NOCHE_DEL_PRESUPUESTO),
                new Presupuesto(new BigDecimal("0.025"), 200, 45));
        assertThat(esperar(() -> estado(corrida), e -> !"en_curso".equals(e))).isEqualTo("cortada");
        assertThat(jdbc.sql("select limite_alcanzado from parte.corridas where id = :id").param("id", corrida)
                .query(String.class).single()).isEqualTo("costo");

        // Lo gastado no pasa el tope, no queda nada reservado, y lo rechazado se anotó.
        assertThat(libro("gastado_usd", corrida)).isLessThanOrEqualTo(new BigDecimal("0.025"));
        assertThat(libro("reservado_usd", corrida)).isZero();
        List<UsoModelo> usos = registro.deCorridaYTipo(corrida, Tipos.USO_MODELO).stream()
                .map(s -> registro.contenido(s, UsoModelo.class)).toList();
        List<UsoModelo> rechazados = usos.stream().filter(u -> Modelo.RECHAZADA.equals(u.resultado())).toList();
        assertThat(rechazados).isNotEmpty().allSatisfy(u -> {
            assertThat(u.limite()).isEqualTo("costo");
            assertThat(u.costoUsd()).isZero();
            assertThat(u.reservaUsd()).isPositive();
        });
        int hechas = usos.size() - rechazados.size();
        assertThat(modelo.llamadas() - llamadasAntes).isEqualTo(hechas);

        // Cada candidato que el refutador no pudo graduar quedó sin procesar: no hay hecho de él.
        long rechazadosAlRefutador = rechazados.stream().filter(u -> "refutador".equals(u.agenteQueLlama())).count();
        CorridaCortada corte = registro.contenido(registro.deCorridaYTipo(corrida, Tipos.CORRIDA_CORTADA).getFirst(),
                CorridaCortada.class);
        assertThat(corte.limite()).isEqualTo("costo");
        assertThat(corte.sinProcesar()).isEqualTo((int) rechazadosAlRefutador).isPositive();
        CorridaTerminada terminada = registro.contenido(registro.deCorridaYTipo(corrida, Tipos.CORRIDA_TERMINADA)
                .getFirst(), CorridaTerminada.class);
        assertThat(terminada.estado()).isEqualTo("cortada");
        assertThat(terminada.conteos().get("PROVEN") + terminada.conteos().get("sinProcesar")).isEqualTo(3);
        assertThat(registro.deCorridaYTipo(corrida, Tipos.HECHOS_ANALIZADOS)).hasSize(terminada.conteos().get("PROVEN"));
        assertThat(jdbc.sql("select corrida_cortada from parte.informes where corrida_id = :c").param("c", corrida)
                .query(Boolean.class).single()).isTrue();

        // La repetición se corta en el mismo lugar, sin llamar al modelo.
        int llamadas = modelo.llamadas();
        String repeticion = inicio.iniciarReproduccion(corrida);
        assertThat(esperar(() -> estado(repeticion), e -> !"en_curso".equals(e))).isEqualTo("cortada");
        ComparacionDeCorridas.Resultado resultado = esperar(() -> comparacion.comparar(corrida, repeticion),
                ComparacionDeCorridas.Resultado::iguales);
        assertThat(resultado.iguales()).as("diferencias: %s / %s", resultado.soloEnOriginal(), resultado.soloEnRepeticion())
                .isTrue();
        assertThat(modelo.llamadas()).isEqualTo(llamadas);
    }

    private String corridaSuelta(String id) {
        jdbc.sql("""
                insert into parte.corridas (id, modo, fecha_objetivo, estado, presupuesto_usd, presupuesto_pasos,
                                            presupuesto_minutos, modelos, iniciada_en)
                values (:id, 'normal', date '2026-09-01', 'en_curso', 1.00, 200, 45, '{}', :ahora)
                """).param("id", id).param("ahora", Timestamp.from(Instant.now())).update();
        jdbc.sql("insert into parte.libros_de_corrida (corrida_id) values (:id)").param("id", id).update();
        return id;
    }

    private BigDecimal libro(String columna, String corrida) {
        return jdbc.sql("select " + columna + " from parte.libros_de_corrida where corrida_id = :c").param("c", corrida)
                .query(BigDecimal.class).single();
    }

    private String estado(String corrida) {
        return jdbc.sql("select estado from parte.corridas where id = :id").param("id", corrida).query(String.class)
                .single();
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
