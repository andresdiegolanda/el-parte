package io.github.andresdiegolanda.elparte.fuentes.impo;

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

/** Una noche con edición del Diario Oficial, de punta a punta, y su repetición sin red (ADR 0010, T30). */
class ExploradorImpoTest extends ConServicios {

    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    JdbcClient jdbc;

    @Test
    void unDecretoQueNombraAlBpsSePublicaConLasPersonasTapadas() {
        String corrida = inicio.iniciarNormal(LocalDate.parse(ImpoDePrueba.EDICION));
        assertThat(esperarFin(corrida)).isEqualTo("completa");

        // Dos normas leídas, la ley y el decreto 300; el 301 falla; la resolución no se pide.
        assertThat(deImpo(corrida, Tipos.EXPLORACION_TERMINADA)).singleElement()
                .satisfies(s -> assertThat(registro.contenido(s, ExploracionTerminada.class))
                        .isEqualTo(new ExploracionTerminada("impo", 2, 1, 1)));
        assertThat(deImpo(corrida, Tipos.FUENTE_FALLIDA)).singleElement()
                .satisfies(s -> assertThat(s.contenido().path("url").asString()).contains("/bases/decretos/301-2026"));
        assertThat(IMPO.pedidos("/bases/decretos/300-2026")).isEqualTo(2);

        // El candidato: la norma, con su URL pública, y el BPS como entidad nombrada.
        Candidato candidato = registro.deCorridaYTipo(corrida, Tipos.CANDIDATOS).stream()
                .map(s -> registro.contenido(s, Candidato.class)).filter(c -> "impo".equals(c.fuente())).findFirst().orElseThrow();
        assertThat(candidato.url()).isEqualTo("https://www.impo.com.uy/bases/decretos/300-2026");
        assertThat(candidato.fechaPublicacion()).isEqualTo("2026-11-05");
        assertThat(candidato.resumen()).contains("Decreto 300/026", "Banco de Previsión Social");
        assertThat(candidato.entidades()).singleElement().satisfies(e -> assertThat(e.entidadId()).isEqualTo("organismo:bps"));
        assertThat(candidato.datosPersonalesEnmascarados()).isEqualTo(2);

        // El hecho quedó en la base, y nada de lo que se guardó tiene a las personas.
        assertThat(jdbc.sql("select count(*) from parte.hechos where clave_de_hecho = 'impo:decretos-300-2026:norma'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select count(*) from parte.grabaciones where corrida_id = :c
                and (contenido::text like '%Fulana%' or contenido::text like '%FULANA%' or contenido::text like '%Mengano%'
                     or contenido::text like '%MENGANO%')
                """).param("c", corrida).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from parte.mensajes where corrida_id = :c and contenido::text ilike '%fulana%'")
                .param("c", corrida).query(Long.class).single()).isZero();

        // Su repetición, sin pedirle nada a IMPO, emite los mismos mensajes.
        int antes = IMPO.pedidos("/bases/decretos/300-2026");
        String repeticion = inicio.iniciarReproduccion(corrida);
        assertThat(esperarFin(repeticion)).isEqualTo("completa");
        assertThat(IMPO.pedidos("/bases/decretos/300-2026")).isEqualTo(antes);
        assertThat(esperar(() -> comparacion.comparar(corrida, repeticion), r -> r.iguales()).iguales()).isTrue();
    }

    private List<Sobre> deImpo(String corrida, String tipo) {
        return esperar(() -> registro.deCorridaYTipo(corrida, tipo).stream()
                .filter(s -> "impo".equals(s.contenido().path("fuente").asString())).toList(), l -> !l.isEmpty());
    }

    private String esperarFin(String corridaId) {
        return esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", corridaId)
                .query(String.class).single(), estado -> !"en_curso".equals(estado));
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
