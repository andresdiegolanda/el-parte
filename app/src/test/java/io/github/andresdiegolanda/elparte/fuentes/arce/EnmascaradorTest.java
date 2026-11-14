package io.github.andresdiegolanda.elparte.fuentes.arce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Los datos personales de una publicación se tapan antes de cualquier otra cosa (ADR 0008). */
class EnmascaradorTest {

    static ArceDePrueba arce;
    static LectorArce lector;

    @BeforeAll
    static void arrancar() {
        arce = new ArceDePrueba().arrancar();
        lector = new LectorArce(JsonMapper.builder().build(), arce.urlBase());
    }

    @AfterAll
    static void parar() {
        arce.close();
    }

    @Test
    void quitaTodosLosContactos() {
        JsonNode publicacion = lector.leer("llamado-1200001").publicacion();
        Enmascarador.Enmascarada enmascarada = Enmascarador.enmascarar(publicacion);

        assertThat(enmascarada.tapados()).isEqualTo(2);
        assertThat(enmascarada.publicacion().toString()).doesNotContain("contactPoint", "Mengano", "@ejemplo.invalid");
        assertThat(publicacion.toString()).contains("Mengano");
    }

    @Test
    void tapaAlProveedorQueNoPareceSociedad() {
        JsonNode publicacion = lector.leer("adjudicacion-1200002").publicacion();
        JsonNode enmascarada = Enmascarador.enmascarar(publicacion).publicacion();

        JsonNode proveedores = enmascarada.path("awards").path(0).path("suppliers");
        assertThat(proveedores.path(0).path("name").asString()).isEqualTo("Distribuidora Ejemplo S.A.");
        assertThat(proveedores.path(0).path("id").asString()).isEqualTo("R210000000019");
        assertThat(proveedores.path(1).path("name").asString()).isEqualTo(Enmascarador.PERSONA);
        assertThat(proveedores.path(1).path("id").asString()).isEqualTo(Enmascarador.PERSONA);

        JsonNode parte = enmascarada.path("parties").path(2);
        assertThat(parte.path("name").asString()).isEqualTo(Enmascarador.PERSONA);
        assertThat(parte.has("identifier")).isFalse();
        assertThat(enmascarada.toString()).doesNotContain("Fulana", "110000000012", "Mengano");
        assertThat(enmascarada.path("parties").path(1).path("identifier").path("id").asString()).isEqualTo("210000000019");
    }

    @Test
    void reconoceLasMarcasDeSociedad() {
        assertThat(Enmascarador.pareceSociedad("Distribuidora Ejemplo S.A.")).isTrue();
        assertThat(Enmascarador.pareceSociedad("Ejemplo SRL")).isTrue();
        assertThat(Enmascarador.pareceSociedad("Servicios Inventados Ltda.")).isTrue();
        assertThat(Enmascarador.pareceSociedad("Cooperativa de Ejemplo")).isTrue();
        assertThat(Enmascarador.pareceSociedad("Fulana de Tal")).isFalse();
        assertThat(Enmascarador.pareceSociedad("")).isFalse();
        assertThat(Enmascarador.pareceSociedad(null)).isFalse();
    }
}
