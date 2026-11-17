package io.github.andresdiegolanda.elparte.fuentes.bid;

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

/** Una noche con operaciones del BID, de punta a punta, y su repetición sin red (ADR 0006, T24). */
class ExploradorBidTest extends ConServicios {

    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    JdbcClient jdbc;

    @Test
    void laOperacionQueNombraAlBpsSePublicaYLaOtraNo() {
        String corrida = inicio.iniciarNormal(LocalDate.parse(BidDePrueba.NOCHE));
        assertThat(esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", corrida)
                .query(String.class).single(), e -> !"en_curso".equals(e))).isEqualTo("completa");

        List<Sobre> terminadas = esperar(() -> registro.deCorridaYTipo(corrida, Tipos.EXPLORACION_TERMINADA).stream()
                .filter(s -> "bid".equals(s.contenido().path("fuente").asString())).toList(), l -> !l.isEmpty());
        assertThat(terminadas).singleElement().satisfies(s -> assertThat(registro.contenido(s, ExploracionTerminada.class))
                .isEqualTo(new ExploracionTerminada("bid", 2, 1, 0)));
        Candidato candidato = registro.deCorridaYTipo(corrida, Tipos.CANDIDATOS).stream()
                .map(s -> registro.contenido(s, Candidato.class)).filter(c -> "bid".equals(c.fuente())).findFirst().orElseThrow();
        assertThat(candidato.url()).isEqualTo("https://www.iadb.org/en/project/UR-L9001");
        assertThat(candidato.tipoDeHecho()).isEqualTo("aprobacion");
        assertThat(candidato.resumen()).contains("UR-L9001", "Banco de Previsión Social");
        assertThat(candidato.evidencia().fragmento()).contains("objtv = ", "(BPS)");
        assertThat(jdbc.sql("select count(*) from parte.hechos where clave_de_hecho = 'bid:UR-L9001:aprobacion'")
                .query(Long.class).single()).isEqualTo(1);

        int antes = BID.pedidos();
        String repeticion = inicio.iniciarReproduccion(corrida);
        assertThat(esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", repeticion)
                .query(String.class).single(), e -> !"en_curso".equals(e))).isEqualTo("completa");
        assertThat(BID.pedidos()).isEqualTo(antes);
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
