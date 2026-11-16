package io.github.andresdiegolanda.elparte;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.fuentes.arce.ArceDePrueba;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoCotejado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CandidatoGraduado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.CorridaTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.ExploracionTerminada;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.HechoAnalizado;
import io.github.andresdiegolanda.elparte.mensajes.Contenidos.UsoModelo;
import io.github.andresdiegolanda.elparte.mensajes.RegistroDeMensajes;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * La primera rebanada de punta a punta, con el ARCE y el modelo de mentira (docs/plan.md, fin de
 * semana 2): una noche normal, su repetición sin red ni costo, y la misma noche corrida otra vez.
 */
class PrimeraRebanadaTest extends ConServicios {

    // La base es la de todas las pruebas: se cuenta solo lo de esta noche de noviembre.
    private static final String HECHOS_DE_ESTA_NOCHE = "h.clave_de_hecho like 'arce:ocds-yfs5dr-12%'";
    private static final String VERSIONES_DE_ESTA_NOCHE = "select count(*) from parte.versiones_de_hecho v "
            + "join parte.hechos h on h.id = v.hecho_id where " + HECHOS_DE_ESTA_NOCHE;

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
    @Autowired
    JdbcClient jdbc;
    @Value("${local.server.port}")
    int puerto;

    private final HttpClient web = HttpClient.newHttpClient();

    @Test
    void unaNocheSuRepeticionYLaMismaNocheOtraVez() throws Exception {
        // 1. Una noche normal.
        String original = inicio.iniciarNormal(LocalDate.parse(ArceDePrueba.NOCHE));
        assertThat(esperarFin(original)).isEqualTo("completa");

        ExploracionTerminada exploracion = unico(original, Tipos.EXPLORACION_TERMINADA, ExploracionTerminada.class);
        assertThat(exploracion).isEqualTo(new ExploracionTerminada("arce", 5, 3, 1));
        assertThat(registro.deCorridaYTipo(original, Tipos.FUENTE_FALLIDA)).hasSize(1)
                .first().satisfies(s -> assertThat(s.contenido().path("url").asString()).endsWith("/release/llamado-1200006"));

        Map<String, String> grados = registro.deCorridaYTipo(original, Tipos.CANDIDATOS_GRADUADOS).stream()
                .map(s -> registro.contenido(s, CandidatoGraduado.class))
                .collect(Collectors.toMap(CandidatoGraduado::claveDeHecho, CandidatoGraduado::grado));
        assertThat(grados).containsOnly(
                Map.entry("arce:ocds-yfs5dr-1200001:llamado", "PROVEN"),
                Map.entry("arce:ocds-yfs5dr-1200002:adjudicacion", "PROVEN"),
                Map.entry("arce:ocds-yfs5dr-1200004:aclaracion", "INFERRED"));

        // El refutador y el analista llaman al modelo una vez por candidato, los dos con Sonnet.
        List<UsoModelo> usos = usos(original);
        assertThat(usos).hasSize(6).allSatisfy(uso -> {
            assertThat(uso.modelo()).isEqualTo("claude-sonnet-5-5");
            assertThat(uso.resultado()).isEqualTo("hecha");
        });
        assertThat(usos).extracting(UsoModelo::agenteQueLlama).containsOnly("refutador", "analista")
                .filteredOn("analista"::equals).hasSize(3);
        assertThat(modelo.modelosPedidos()).containsOnly("claude-sonnet-5-5");
        CorridaTerminada terminada = unico(original, Tipos.CORRIDA_TERMINADA, CorridaTerminada.class);
        assertThat(terminada.costoUsd()).isEqualByComparingTo(new BigDecimal("0.024"));

        // El analista copió el grado del refutador y escribió por qué importa y qué mirar.
        assertThat(registro.deCorridaYTipo(original, Tipos.HECHOS_ANALIZADOS)).hasSize(3)
                .extracting(s -> registro.contenido(s, HechoAnalizado.class)).allSatisfy(hecho -> {
                    assertThat(hecho.grado()).isEqualTo(grados.get(hecho.claveDeHecho()));
                    assertThat(hecho.porQueImporta()).startsWith("Es una compra de una entidad que seguís");
                    assertThat(hecho.queMirar()).doesNotStartWith("Sin análisis");
                });
        assertThat(terminada.conteos()).containsEntry("candidatos", 3).containsEntry("nuevos", 3)
                .containsEntry("PROVEN", 2).containsEntry("INFERRED", 1).containsEntry("sinProcesar", 1);

        // Lo que quedó en la base: tres hechos, la aclaración a revisar y fuera de la noche leída.
        assertThat(contar("select count(*) from parte.hechos h where " + HECHOS_DE_ESTA_NOCHE)).isEqualTo(3);
        assertThat(contar(VERSIONES_DE_ESTA_NOCHE)).isEqualTo(3);
        assertThat(jdbc.sql("""
                select v.estado_revision || ' ' || v.fecha_confirmada from parte.versiones_de_hecho v
                join parte.hechos h on h.id = v.hecho_id where h.tipo_de_hecho = 'aclaracion' and\s""" + HECHOS_DE_ESTA_NOCHE)
                .query(String.class).single()).isEqualTo("pendiente false");
        assertThat(contar("select count(*) from parte.entidades where id = 'persona_juridica:uy-rut-210000000019'")).isEqualTo(1);
        assertThat(contar("select count(*) from parte.entidades where id like '%110000000012%'")).isZero();
        assertThat(jdbc.sql("""
                select count(*) from parte.informes i join parte.informe_hechos ih on ih.informe_id = i.id
                join parte.clientes c on c.cliente_id = i.cliente_id
                where i.corrida_id = :c and not i.sin_novedades and c.es_demo
                """).param("c", original).query(Long.class).single()).isEqualTo(3);

        // Ningún dato personal llegó a los mensajes, a las grabaciones ni a la base (ADR 0008).
        for (String tabla : List.of("parte.mensajes", "parte.grabaciones")) {
            assertThat(contar("select count(*) from " + tabla + " t where t.contenido::text ~ 'Fulana|Mengano|ejemplo\\.invalid|110000000012'"))
                    .as(tabla).isZero();
        }
        assertThat(contar("""
                select count(*) from parte.versiones_de_hecho v join parte.evidencias e on e.version_id = v.id
                where v.afirmacion ~ 'Fulana|Mengano' or e.fragmento ~ 'Fulana|Mengano'
                """)).isZero();

        // 2. La misma corrida repetida: sin red, sin modelo, mensajes idénticos, y la base no cambia.
        int pedidosAArce = ARCE.pedidos();
        int llamadasAlModelo = modelo.llamadas();
        String repeticion = inicio.iniciarReproduccion(original);
        assertThat(esperarFin(repeticion)).isEqualTo("completa");

        ComparacionDeCorridas.Resultado resultado = esperar(() -> comparacion.comparar(original, repeticion),
                ComparacionDeCorridas.Resultado::iguales);
        assertThat(resultado.iguales()).as("diferencias: %s / %s", resultado.soloEnOriginal(), resultado.soloEnRepeticion())
                .isTrue();
        assertThat(ARCE.pedidos()).isEqualTo(pedidosAArce);
        assertThat(modelo.llamadas()).isEqualTo(llamadasAlModelo);
        assertThat(usos(repeticion)).hasSize(6).allSatisfy(uso -> assertThat(uso.resultado()).isEqualTo("reproducida"));
        assertThat(contar(VERSIONES_DE_ESTA_NOCHE)).isEqualTo(3);
        // Los informes son de la original, uno por cliente que sigue al BPS; la repetición no arma informes.
        assertThat(contar("select count(*) from parte.informes where corrida_id in ('" + original + "', '" + repeticion + "')"))
                .isEqualTo(3);

        // 3. Las páginas.
        String manana = pagina("/manana");
        assertThat(manana).contains("Banco de Previsión Social publicó el llamado Compra Directa 900/2026",
                "PROVEN", "INFERRED", "pendiente de revisión", "ARCE lo publicó con atraso",
                "https://www.comprasestatales.gub.uy/ocds/release/llamado-1200001",
                "lectura de la fuente falló", "puede faltar algo",
                "Por qué importa:", "Es una compra de una entidad que seguís (adjudicacion).", "Qué mirar:");
        assertThat(manana).doesNotContain("Fulana");
        assertThat(pagina("/corridas")).contains(original, repeticion);
        assertThat(pagina("/corridas/" + original)).contains("k-arce-0001", "k-arce-0003", "fuente.fallida", "refutador");
        assertThat(pagina("/corridas/" + repeticion)).contains("Los mensajes son idénticos");

        // 4. La revisión humana de la aclaración INFERRED (D6): está en la cola, un rechazo sin nota no
        // entra, el rechazo con nota sí, una segunda revisión no pisa la primera, y la página de la mañana
        // lo dice sin esconder el hecho.
        long aclaracion = jdbc.sql("""
                select v.id from parte.versiones_de_hecho v join parte.hechos h on h.id = v.hecho_id
                where h.clave_de_hecho = 'arce:ocds-yfs5dr-1200004:aclaracion'
                """).query(Long.class).single();
        // La cola es del responsable: entra con su enlace.
        Navegador responsable = new Navegador(puerto);
        responsable.entrarComo(RESPONSABLE, buzon);
        assertThat(responsable.get("/revision").body()).contains("version-" + aclaracion, "Se deduce de:",
                "La publicación es una enmienda del llamado 1200004");
        assertThat(revisar(responsable, aclaracion, "rechazado", "")).isEqualTo(400);
        assertThat(revisar(responsable, aclaracion, "a-medias", "nota")).isEqualTo(400);
        assertThat(revisar(responsable, aclaracion, "rechazado", "El documento no dice que se prorrogue nada.")).isEqualTo(302);
        assertThat(revisar(responsable, aclaracion, "aceptado", "")).isEqualTo(409);
        assertThat(jdbc.sql("select estado_revision from parte.versiones_de_hecho where id = :v").param("v", aclaracion)
                .query(String.class).single()).isEqualTo("rechazado");
        assertThat(responsable.get("/revision").body()).doesNotContain("version-" + aclaracion)
                .contains("El documento no dice que se prorrogue nada.", "no se sostiene");
        assertThat(pagina("/manana")).contains("revisado: no se sostiene",
                "En la revisión se vio que esta deducción no se sostiene", "El documento no dice que se prorrogue nada.",
                "Banco de Previsión Social publicó el llamado Compra Directa 900/2026");

        // 5. La misma noche otra vez: el archivista ya conoce los tres hechos, y no se llama al modelo.
        String otraVez = inicio.iniciarNormal(LocalDate.parse(ArceDePrueba.NOCHE));
        assertThat(otraVez).isNotEqualTo(original);
        assertThat(esperarFin(otraVez)).isEqualTo("completa");
        assertThat(registro.deCorridaYTipo(otraVez, Tipos.CANDIDATOS_COTEJADOS)).hasSize(3)
                .allSatisfy(s -> assertThat(registro.contenido(s, CandidatoCotejado.class).estado()).isEqualTo("conocido"));
        assertThat(modelo.llamadas()).isEqualTo(llamadasAlModelo);
        assertThat(contar(VERSIONES_DE_ESTA_NOCHE)).isEqualTo(3);
        // Un informe por cliente que sigue al BPS: la demostración y los dos invitados de las pruebas.
        // Los informes se arman después de que la corrida termina: se los espera.
        assertThat(esperar(() -> jdbc.sql("select sin_novedades from parte.informes where corrida_id = :c").param("c", otraVez)
                .query(Boolean.class).list(), lista -> lista.size() == 3)).hasSize(3).containsOnly(true);

        // 6. Una noche en la que ARCE no publicó nada, como un domingo: la página lo dice, y como es la más
        // reciente, es la que muestra. La de antes se elige en la lista de noches.
        String domingo = inicio.iniciarNormal(LocalDate.parse("2026-11-22"));
        assertThat(esperarFin(domingo)).isEqualTo("completa");
        assertThat(unico(domingo, Tipos.EXPLORACION_TERMINADA, ExploracionTerminada.class).publicacionesLeidas()).isZero();
        esperar(() -> jdbc.sql("select count(*) from parte.informes where corrida_id = :c").param("c", domingo)
                .query(Long.class).single(), n -> n == 3);
        String vacia = pagina("/manana");
        assertThat(vacia).contains("2026-11-22", "ARCE no publicó nada esa noche", "Noches con parte:",
                "href=\"/manana?noche=" + ArceDePrueba.NOCHE + "\"");
        // De esa noche muestra su corrida más reciente: la del paso 5, que no encontró nada nuevo.
        assertThat(pagina("/manana?noche=" + ArceDePrueba.NOCHE)).contains("Noche del <strong>" + ArceDePrueba.NOCHE, otraVez)
                .doesNotContain("ARCE no publicó nada");
        assertThat(pagina("/manana?noche=2020-01-01")).contains("No hay un parte de la noche del <span>2020-01-01</span>");
        assertThat(pagina("/manana?noche=cualquiera")).contains("2026-11-22");
    }

    private static int revisar(Navegador navegador, long version, String estado, String nota) {
        return navegador.post("/revision", "/revision/" + version, Map.of("estado", estado, "nota", nota)).statusCode();
    }

    private String esperarFin(String corridaId) {
        return esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", corridaId)
                .query(String.class).single(), estado -> !"en_curso".equals(estado));
    }

    private static <T> T esperar(Supplier<T> valor, java.util.function.Predicate<T> listo) {
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

    private <T> T unico(String corridaId, String tipo, Class<T> clase) {
        // El estado de la corrida cambia en la base antes de que el observador guarde el mensaje: se lo espera.
        List<Sobre> sobres = esperar(() -> registro.deCorridaYTipo(corridaId, tipo), lista -> !lista.isEmpty());
        assertThat(sobres).as(tipo).hasSize(1);
        return registro.contenido(sobres.getFirst(), clase);
    }

    private List<UsoModelo> usos(String corridaId) {
        return registro.deCorridaYTipo(corridaId, Tipos.USO_MODELO).stream()
                .map(s -> registro.contenido(s, UsoModelo.class)).toList();
    }

    private long contar(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private String pagina(String ruta) throws IOException, InterruptedException {
        HttpResponse<String> respuesta = web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        // Una copia para mirarla en un navegador: target/paginas/.
        Path copia = Path.of("target", "paginas", ruta.substring(1).replace('/', '_') + ".html");
        Files.createDirectories(copia.getParent());
        Files.writeString(copia, respuesta.body());
        return respuesta.body();
    }
}
