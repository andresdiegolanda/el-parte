package io.github.andresdiegolanda.elparte.fuentes.arce;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.fuentes.arce.ExploradorArce.Leido;
import io.github.andresdiegolanda.elparte.fuentes.arce.LectorArce.ItemDelFeed;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;

/** El orden en que los candidatos entran al tope de la corrida: la fecha objetivo antes que el margen. */
class ExploradorArceTest {

    private static final LocalDate NOCHE = LocalDate.parse("2026-11-12");

    @Test
    void laFechaObjetivoEntraAntesQueElDiaDeMargen() {
        List<Leido> leidos = List.of(
                leido("margen-temprano", LocalDateTime.parse("2026-11-11T09:00:00"), true),
                leido("margen-sin-candidato", LocalDateTime.parse("2026-11-11T10:00:00"), false),
                leido("margen-tarde", LocalDateTime.parse("2026-11-11T16:00:00"), true),
                leido("noche-temprano", LocalDateTime.parse("2026-11-12T08:00:00"), true),
                leido("noche-tarde", LocalDateTime.parse("2026-11-12T17:00:00"), true));

        assertThat(ExploradorArce.paraElTope(leidos, NOCHE)).extracting(Candidato::identificadorEnFuente)
                .containsExactly("noche-temprano", "noche-tarde", "margen-temprano", "margen-tarde");
    }

    private static Leido leido(String id, LocalDateTime publicada, boolean conCandidato) {
        Optional<Candidato> candidato = conCandidato
                ? Optional.of(new Candidato(null, "arce", id, null, null, publicada.toString(), "llamado", List.of(), "", null, 0))
                : Optional.empty();
        return new Leido(new ItemDelFeed(id, publicada, "llamado"), candidato);
    }
}
