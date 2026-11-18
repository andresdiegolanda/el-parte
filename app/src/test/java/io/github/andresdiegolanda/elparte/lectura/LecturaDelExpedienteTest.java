package io.github.andresdiegolanda.elparte.lectura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.BuzonDePrueba;
import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.ModeloDePrueba;
import io.github.andresdiegolanda.elparte.Navegador;
import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionesGraduadas;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ObservacionesPropuestas;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * Una lectura del expediente de punta a punta (ADR 0018, T47), con una entidad que solo existe en esta
 * prueba: tres hechos en la ventana, uno deducido, uno rechazado en la revisión y uno viejo que quedan
 * afuera. El modelo de mentira propone seis observaciones: el control descarta cuatro y el refutador
 * sostiene dos, una limitada a INFERRED por el hecho deducido que cita.
 */
class LecturaDelExpedienteTest extends ConServicios {

    private static final String ENTIDAD = "organismo:prueba-lectura";

    @Autowired
    JdbcClient jdbc;
    @Autowired
    BuzonDePrueba buzon;
    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    RegistroDeMensajes registro;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    ModeloDePrueba modelo;
    @Value("${local.server.port}")
    int puerto;

    @Test
    void elModeloLeeElExpedienteYQuedanLasObservacionesQueSeSostienen() {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Montevideo"));
        datos(hoy);

        Navegador invitada = new Navegador(puerto);
        invitada.entrarComo(INVITADA, buzon);
        assertThat(invitada.get("/expediente/" + ENTIDAD).body()).doesNotContain("Leer los últimos");
        assertThat(invitada.post("/perfil", "/expediente/" + ENTIDAD + "/lectura", Map.of()).statusCode()).isEqualTo(403);

        Navegador responsable = new Navegador(puerto);
        responsable.entrarComo(RESPONSABLE, buzon);
        assertThat(responsable.get("/expediente/" + ENTIDAD).body()).contains("Leer los últimos 90 días con el modelo, hasta 0.50 USD");
        int antes = modelo.llamadas();
        String destino = responsable.post("/expediente/" + ENTIDAD, "/expediente/" + ENTIDAD + "/lectura", Map.of())
                .headers().firstValue("Location").orElseThrow();
        String corrida = destino.substring(destino.indexOf("lectura=") + "lectura=".length());
        assertThat(corrida).startsWith("c-" + hoy + "-lectura");
        assertThat(esperar(() -> estado(corrida), e -> !"en_curso".equals(e))).isEqualTo("completa");
        // Un pedido al lector y uno al refutador por cada observación que pasó el control.
        assertThat(modelo.llamadas() - antes).isEqualTo(3);

        assertThat(jdbc.sql("""
                select hechos_leidos || ' ' || hechos_cortados || ' ' || propuestas || ' ' || descartadas
                from parte.lecturas_de_expediente where corrida_id = :id
                """).param("id", corrida).query(String.class).single()).isEqualTo("3 0 6 4");
        assertThat(jdbc.sql("select grado || ' ' || clase from parte.observaciones where corrida_id = :id order by numero")
                .param("id", corrida).query(String.class).list()).containsExactly("PROVEN repeticion", "INFERRED cadena");

        ObservacionesPropuestas propuestas = registro.contenido(esperar(
                () -> registro.deCorridaYTipo(corrida, Tipos.OBSERVACIONES_PROPUESTAS), l -> !l.isEmpty()).getFirst(),
                ObservacionesPropuestas.class);
        assertThat(propuestas.hechosLeidos()).isEqualTo(3);
        assertThat(propuestas.observaciones()).extracting(o -> o.descarte() == null ? "pasa" : o.descarte().split(" ")[0])
                .containsExactly("pasa", "pasa", "cita", "tiene", "califica,", "cita");
        ObservacionesGraduadas graduadas = registro.contenido(esperar(
                () -> registro.deCorridaYTipo(corrida, Tipos.OBSERVACIONES_GRADUADAS), l -> !l.isEmpty()).getFirst(),
                ObservacionesGraduadas.class);
        assertThat(graduadas.descartadas()).isEqualTo(4);
        assertThat(graduadas.observaciones().get(1).gradoDelRefutador()).isEqualTo("PROVEN");
        assertThat(graduadas.observaciones().get(1).grado()).isEqualTo("INFERRED");
        // No es una noche: no hay informes ni avisos.
        assertThat(registro.deCorridaYTipo(corrida, Tipos.INFORMES)).isEmpty();

        String pagina = new Navegador(puerto).get("/expediente/" + ENTIDAD).body();
        assertThat(pagina).contains("Lo que leyó el modelo", "3</span> hechos leídos", "compró sillas en dos hechos",
                "primero publicado y después prorrogado", "#arce:ocds-lectura-1:llamado", "Cita un hecho deducido")
                .doesNotContain("999999", "Es irregular", "Leer los últimos");

        int antesDeRepetir = modelo.llamadas();
        String repeticion = inicio.iniciarReproduccion(corrida);
        assertThat(esperar(() -> estado(repeticion), e -> !"en_curso".equals(e))).isEqualTo("completa");
        assertThat(modelo.llamadas()).isEqualTo(antesDeRepetir);
        assertThat(esperar(() -> comparacion.comparar(corrida, repeticion), r -> r.iguales()).iguales()).isTrue();
    }

    private String estado(String corrida) {
        return jdbc.sql("select estado from parte.corridas where id = :id").param("id", corrida).query(String.class).single();
    }

    private void datos(LocalDate hoy) {
        if (jdbc.sql("select count(*) from parte.entidades where id = :id").param("id", ENTIDAD).query(Long.class).single() > 0) {
            return;
        }
        jdbc.sql("insert into parte.entidades (id, tipo, nombre) values (:id, 'organismo', 'Organismo de prueba de la lectura')")
                .param("id", ENTIDAD).update();
        String vieja = corrida(hoy.minusDays(200), "a");
        String primera = corrida(hoy.minusDays(20), "b");
        String segunda = corrida(hoy.minusDays(15), "c");
        String tercera = corrida(hoy.minusDays(10), "d");
        version(hecho("arce:ocds-lectura-1:llamado"), "PROVEN", null, "no_requiere", primera, "l1",
                "El organismo de prueba llamó a licitación por sillas.");
        version(hecho("arce:ocds-lectura-2:llamado"), "INFERRED", "La aclaración cambia la fecha de apertura.", "pendiente",
                segunda, "l2", "El organismo de prueba prorrogó la apertura de la licitación de sillas.");
        version(hecho("arce:ocds-lectura-3:adjudicacion"), "PROVEN", null, "no_requiere", tercera, "l3",
                "El organismo de prueba adjudicó la compra de sillas.");
        version(hecho("arce:ocds-lectura-4:adjudicacion"), "PROVEN", null, "rechazado", tercera, "l4",
                "El organismo de prueba adjudicó algo que no se sostuvo.");
        version(hecho("arce:ocds-lectura-5:llamado"), "PROVEN", null, "no_requiere", vieja, "l5",
                "El organismo de prueba llamó a licitación hace mucho.");
    }

    private String corrida(LocalDate noche, String sufijo) {
        String id = "c-" + noche + "-normal-lec" + sufijo;
        jdbc.sql("""
                insert into parte.corridas (id, modo, fecha_objetivo, estado, presupuesto_usd, presupuesto_pasos,
                    presupuesto_minutos, modelos, iniciada_en, terminada_en)
                values (:id, 'normal', :noche, 'completa', 1, 1, 1, '{}', now(), now())
                """).param("id", id).param("noche", noche).update();
        return id;
    }

    private long hecho(String clave) {
        long id = jdbc.sql("insert into parte.hechos (clave_de_hecho, fuente_id, tipo_de_hecho) values (:c, 'arce', :t) returning id")
                .param("c", clave).param("t", clave.substring(clave.lastIndexOf(':') + 1)).query(Long.class).single();
        jdbc.sql("insert into parte.hecho_entidades (hecho_id, entidad_id, rol) values (:h, :e, 'comprador')")
                .param("h", id).param("e", ENTIDAD).update();
        return id;
    }

    private void version(long hecho, String grado, String razonamiento, String revision, String corrida, String mensajes,
            String afirmacion) {
        for (String paso : new String[] {"candidato", "cotejado", "graduado", "analizado"}) {
            jdbc.sql("""
                    insert into parte.mensajes (mensaje_id, tipo, version, corrida_id, agente, emitido_en, contenido)
                    values (:id, 'prueba', 1, :corrida, 'prueba', now(), '{}')
                    """).param("id", mensajes + "-" + paso).param("corrida", corrida).update();
        }
        jdbc.sql("""
                insert into parte.versiones_de_hecho (hecho_id, numero, afirmacion, grado, razonamiento, fecha_publicacion,
                    fecha_confirmada, que_dice, por_que_importa, que_mirar, estado_revision, corrida_id,
                    mensaje_candidato, mensaje_cotejado, mensaje_graduado, mensaje_analizado)
                values (:h, 1, :a, :g, :r, '2026-07-02T10:15:00-03:00', true, :a, 'Importa por la prueba.',
                    'Mirar la prueba.', :rev, :corrida, :m || '-candidato', :m || '-cotejado', :m || '-graduado',
                    :m || '-analizado')
                """).param("h", hecho).param("a", afirmacion).param("g", grado).param("r", razonamiento).param("rev", revision)
                .param("corrida", corrida).param("m", mensajes).update();
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
