package io.github.andresdiegolanda.elparte.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.Navegador;

/**
 * El expediente de cada entidad (T33). Los hechos son de una entidad que solo existe en esta prueba, con
 * dos corridas de junio y julio que ninguna otra prueba usa: uno con dos versiones, la segunda deducida.
 */
class ExpedienteTest extends ConServicios {

    private static final String ENTIDAD = "organismo:prueba-expediente";

    @Autowired
    JdbcClient jdbc;
    @Value("${local.server.port}")
    int puerto;

    @BeforeEach
    void datos() {
        if (jdbc.sql("select count(*) from parte.entidades where id = :id").param("id", ENTIDAD).query(Long.class).single() > 0) {
            return;
        }
        jdbc.sql("insert into parte.entidades (id, tipo, nombre) values (:id, 'organismo', 'Organismo de prueba del expediente')")
                .param("id", ENTIDAD).update();
        corrida("c-2026-06-10-normal-exp", "2026-06-10");
        corrida("c-2026-07-02-normal-exp", "2026-07-02");
        long llamado = hecho("arce:ocds-expediente-1:llamado", "llamado");
        long adjudicacion = hecho("arce:ocds-expediente-2:adjudicacion", "adjudicacion");
        version(llamado, 1, "PROVEN", null, "no_requiere", "c-2026-06-10-normal-exp", "e1",
                "El organismo de prueba llamó a licitación por sillas.");
        version(llamado, 2, "INFERRED", "La aclaración cambia la fecha de apertura.", "pendiente", "c-2026-07-02-normal-exp", "e2",
                "El organismo de prueba prorrogó la apertura de la licitación de sillas.");
        version(adjudicacion, 1, "PROVEN", null, "no_requiere", "c-2026-07-02-normal-exp", "e3",
                "El organismo de prueba adjudicó la compra de mesas.");
    }

    @Test
    void cadaHechoConTodasSusVersionesMesPorMes() {
        Navegador anonimo = new Navegador(puerto);
        assertThat(anonimo.get("/expediente").body()).contains("Organismo de prueba del expediente", "/expediente/" + ENTIDAD);
        assertThat(anonimo.get("/").body()).contains("href=\"/expediente\"");

        // Sin mes, el más reciente: julio, con la adjudicación que apareció ese mes.
        String julio = guardar("expediente-julio", anonimo.get("/expediente/" + ENTIDAD).body());
        assertThat(julio).contains("Mes por mes", "2026-07", "2026-06", "Los hechos de <span>2026-07</span>",
                "adjudicó la compra de mesas", "/corridas/c-2026-07-02-normal-exp#e3-candidato")
                .doesNotContain("llamó a licitación por sillas");

        // Junio: el llamado, con sus dos versiones en orden, la segunda pendiente de revisión.
        String junio = guardar("expediente-junio", anonimo.get("/expediente/" + ENTIDAD + "?mes=2026-06").body());
        assertThat(junio).contains("llamó a licitación por sillas", "prorrogó la apertura", "Versión 2",
                "pendiente de revisión", "La aclaración cambia la fecha de apertura.", "2 versiones");
        assertThat(junio.indexOf("llamó a licitación por sillas")).isLessThan(junio.indexOf("prorrogó la apertura"));

        assertThat(anonimo.get("/expediente/organismo:no-existe").statusCode()).isEqualTo(404);
    }

    private void corrida(String id, String noche) {
        jdbc.sql("""
                insert into parte.corridas (id, modo, fecha_objetivo, estado, presupuesto_usd, presupuesto_pasos,
                    presupuesto_minutos, modelos, iniciada_en, terminada_en)
                values (:id, 'normal', :noche, 'completa', 1, 1, 1, '{}', now(), now())
                """).param("id", id).param("noche", LocalDate.parse(noche)).update();
    }

    private long hecho(String clave, String tipo) {
        long id = jdbc.sql("insert into parte.hechos (clave_de_hecho, fuente_id, tipo_de_hecho) values (:c, 'arce', :t) returning id")
                .param("c", clave).param("t", tipo).query(Long.class).single();
        jdbc.sql("insert into parte.hecho_entidades (hecho_id, entidad_id, rol) values (:h, :e, 'comprador')")
                .param("h", id).param("e", ENTIDAD).update();
        return id;
    }

    private void version(long hecho, int numero, String grado, String razonamiento, String revision, String corrida,
            String mensajes, String afirmacion) {
        for (String paso : new String[] {"candidato", "cotejado", "graduado", "analizado"}) {
            jdbc.sql("""
                    insert into parte.mensajes (mensaje_id, tipo, version, corrida_id, agente, emitido_en, contenido)
                    values (:id, 'prueba', 1, :corrida, 'prueba', now(), '{}')
                    """).param("id", mensajes + "-" + paso).param("corrida", corrida).update();
        }
        long version = jdbc.sql("""
                insert into parte.versiones_de_hecho (hecho_id, numero, afirmacion, grado, razonamiento, fecha_publicacion,
                    fecha_confirmada, que_dice, por_que_importa, que_mirar, estado_revision, corrida_id,
                    mensaje_candidato, mensaje_cotejado, mensaje_graduado, mensaje_analizado)
                values (:h, :n, :a, :g, :r, '2026-07-02T10:15:00-03:00', true, :a, 'Importa por la prueba.',
                    'Mirar la prueba.', :rev, :corrida, :m || '-candidato', :m || '-cotejado', :m || '-graduado',
                    :m || '-analizado')
                returning id
                """).param("h", hecho).param("n", numero).param("a", afirmacion).param("g", grado)
                .param("r", razonamiento).param("rev", revision).param("corrida", corrida).param("m", mensajes)
                .query(Long.class).single();
        jdbc.sql("insert into parte.evidencias (version_id, url, fragmento) values (:v, 'https://ejemplo.test/compra', :f)")
                .param("v", version).param("f", "Fragmento de la versión " + numero).update();
    }

    private static String guardar(String nombre, String html) {
        try {
            Path carpeta = Path.of("target", "paginas");
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve(nombre + ".html"), html);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return html;
    }
}
