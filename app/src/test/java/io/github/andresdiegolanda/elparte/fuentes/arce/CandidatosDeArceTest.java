package io.github.andresdiegolanda.elparte.fuentes.arce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Candidato;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.EntidadEnCandidato;
import tools.jackson.databind.json.JsonMapper;

/** El explorador de ARCE arma los candidatos sin el modelo, con los campos de la publicación. */
class CandidatosDeArceTest {

    static ArceDePrueba arce;
    static LectorArce lector;
    static JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void arrancar() {
        arce = new ArceDePrueba().arrancar();
        lector = new LectorArce(json, arce.urlBase(), 0);
    }

    @AfterAll
    static void parar() {
        arce.close();
    }

    private static Candidato armar(String id) {
        LectorArce.Leida leida = lector.leer(id);
        Enmascarador.Enmascarada enmascarada = Enmascarador.enmascarar(leida.publicacion());
        return CandidatosDeArce.armar("k-arce-0001", enmascarada.publicacion(), "organismo:bps",
                enmascarada.tapados(), leida.hashContenido());
    }

    @Test
    void tipoDeHechoSegunLaEtiqueta() {
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"tender\"]}"))).isEqualTo("llamado");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"tenderAmendment\"]}"))).isEqualTo("aclaracion");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"tenderUpdate\"]}"))).isEqualTo("aclaracion");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"award\"]}"))).isEqualTo("adjudicacion");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"awardUpdate\"]}"))).isEqualTo("adjudicacion");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"tenderCancellation\"]}"))).isEqualTo("cancelacion");
        assertThat(CandidatosDeArce.tipoDeHecho(json.readTree("{\"tag\":[\"planning\"]}"))).isEqualTo("aviso");
    }

    @Test
    void unLlamado() {
        Candidato candidato = armar("llamado-1200001");

        assertThat(candidato.tipoDeHecho()).isEqualTo("llamado");
        assertThat(candidato.proceso()).isEqualTo("ocds-yfs5dr-1200001");
        assertThat(candidato.url()).isEqualTo("https://www.comprasestatales.gub.uy/ocds/release/llamado-1200001");
        assertThat(candidato.fechaPublicacion()).isEqualTo("2026-11-12T10:15:00");
        assertThat(candidato.resumen()).isEqualTo("Banco de Previsión Social publicó el llamado Compra Directa 900/2026 "
                + "(Adquisición de papel para impresoras), con 1 ítem. Recibe ofertas hasta el 2026-11-19 14:00.");
        assertThat(candidato.evidencia().fragmento()).contains("tender.title = Compra Directa 900/2026",
                "tender.tenderPeriod.endDate = 2026-11-19T14:00:00Z");
        assertThat(candidato.evidencia().hashContenido()).startsWith("sha256:").hasSize(71);
        assertThat(candidato.datosPersonalesEnmascarados()).isEqualTo(2);
        assertThat(candidato.entidades()).containsExactly(
                new EntidadEnCandidato("comprador", "organismo:bps", "Banco de Previsión Social"));
    }

    @Test
    void unaAdjudicacionConUnaPersonaTapada() {
        Candidato candidato = armar("adjudicacion-1200002");

        assertThat(candidato.tipoDeHecho()).isEqualTo("adjudicacion");
        assertThat(candidato.resumen()).isEqualTo("Banco de Previsión Social adjudicó la compra 1200002 a "
                + "Distribuidora Ejemplo S.A., [persona]: Tóner negro, por 125000 UYU.");
        assertThat(candidato.entidades()).containsExactly(
                new EntidadEnCandidato("comprador", "organismo:bps", "Banco de Previsión Social"),
                new EntidadEnCandidato("proveedor", "persona_juridica:uy-rut-210000000019", "Distribuidora Ejemplo S.A."),
                new EntidadEnCandidato("proveedor", null, Enmascarador.PERSONA));
        assertThat(candidato.toString()).doesNotContain("Fulana", "110000000012");
    }

    @Test
    void unaAclaracion() {
        Candidato candidato = armar("aclaracion_1200004");

        assertThat(candidato.tipoDeHecho()).isEqualTo("aclaracion");
        assertThat(candidato.resumen()).isEqualTo("Banco de Previsión Social publicó una aclaración del llamado "
                + "1200004: Se aclara el lugar de entrega de la mercadería");
    }

    @Test
    void fechaLegible() {
        assertThat(CandidatosDeArce.fechaLegible("2026-11-17T15:30:00Z")).isEqualTo("2026-11-17 15:30");
        assertThat(CandidatosDeArce.fechaLegible("2026-11-17")).isEqualTo("2026-11-17");
    }
}
