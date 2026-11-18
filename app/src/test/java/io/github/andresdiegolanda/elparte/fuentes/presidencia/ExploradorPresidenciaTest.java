package io.github.andresdiegolanda.elparte.fuentes.presidencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ExploracionTerminada;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/** Una noche con resoluciones de Presidencia, de punta a punta, y su repetición sin red (ADR 0006, T24). */
class ExploradorPresidenciaTest extends ConServicios {

    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    LectorPresidencia lector;

    @Test
    void elListadoSeLeeSoloHastaLaNocheYPorBiseccion() {
        int antes = PRESIDENCIA.pedidos();
        List<LectorPresidencia.Entrada> delDia = lector.resoluciones(LocalDate.parse(PresidenciaDePrueba.NOCHE));
        assertThat(delDia).hasSize(6).allMatch(e -> e.fecha().equals(LocalDate.parse(PresidenciaDePrueba.NOCHE)));
        // La primera página, la segunda (por bisección, donde empieza el 3) y la tercera (donde termina).
        assertThat(PRESIDENCIA.pedidos() - antes).isEqualTo(3);
        assertThat(lector.resoluciones(LocalDate.parse("2026-11-05"))).hasSize(10);
        assertThat(lector.resoluciones(LocalDate.parse("2026-10-03"))).isEmpty();
    }

    @Test
    void laResolucionDelBpsSePublicaConElNombreTapado() {
        String corrida = inicio.iniciarNormal(LocalDate.parse(PresidenciaDePrueba.NOCHE));
        assertThat(esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", corrida)
                .query(String.class).single(), e -> !"en_curso".equals(e))).isEqualTo("completa");

        List<Sobre> terminadas = esperar(() -> registro.deCorridaYTipo(corrida, Tipos.EXPLORACION_TERMINADA).stream()
                .filter(s -> "presidencia".equals(s.contenido().path("fuente").asString())).toList(), l -> !l.isEmpty());
        assertThat(terminadas).singleElement().satisfies(s -> assertThat(registro.contenido(s, ExploracionTerminada.class))
                .isEqualTo(new ExploracionTerminada("presidencia", 6, 1, 0)));
        Candidato candidato = registro.deCorridaYTipo(corrida, Tipos.CANDIDATOS).stream()
                .map(s -> registro.contenido(s, Candidato.class)).filter(c -> "presidencia".equals(c.fuente()))
                .findFirst().orElseThrow();
        assertThat(candidato.url()).isEqualTo("https://www.gub.uy/presidencia/node/" + PresidenciaDePrueba.NODO);
        assertThat(candidato.tipoDeHecho()).isEqualTo("resolucion");
        assertThat(candidato.resumen()).contains("[persona]", "Directorio del Banco de Previsión Social")
                .doesNotContain("Pedro", "Ficticio");
        // El resumen viene de la página, entero, no del listado, que lo corta.
        assertThat(candidato.evidencia().fragmento()).contains("en representación del Poder Ejecutivo", "presidencia_ps_9001.pdf");
        assertThat(jdbc.sql("select count(*) from parte.hechos where clave_de_hecho = :clave")
                .param("clave", "presidencia:" + PresidenciaDePrueba.NODO + ":resolucion").query(Long.class).single()).isEqualTo(1);

        // Ni el nombre ni la ruta, que lo lleva, quedan guardados en ningún lado (ADR 0008).
        for (String tabla : List.of("parte.grabaciones", "parte.mensajes")) {
            for (String dato : List.of("Ficticio", "pedro-ficticio", "Ana Inventada")) {
                assertThat(jdbc.sql("select count(*) from " + tabla + " where corrida_id = :id and contenido::text like :dato")
                        .param("id", corrida).param("dato", "%" + dato + "%").query(Long.class).single())
                        .as("%s en %s", dato, tabla).isZero();
            }
        }
        assertThat(jdbc.sql("select count(*) from parte.versiones_de_hecho where afirmacion like '%Ficticio%'").query(Long.class).single()).isZero();

        int antes = PRESIDENCIA.pedidos();
        String repeticion = inicio.iniciarReproduccion(corrida);
        assertThat(esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", repeticion)
                .query(String.class).single(), e -> !"en_curso".equals(e))).isEqualTo("completa");
        assertThat(PRESIDENCIA.pedidos()).isEqualTo(antes);
        assertThat(esperar(() -> comparacion.comparar(corrida, repeticion), r -> r.iguales()).iguales()).isTrue();
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
