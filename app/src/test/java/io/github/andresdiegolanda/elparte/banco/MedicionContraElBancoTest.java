package io.github.andresdiegolanda.elparte.banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
import io.github.andresdiegolanda.elparte.ModeloDePrueba;
import io.github.andresdiegolanda.elparte.corrida.ComparacionDeCorridas;
import io.github.andresdiegolanda.elparte.corrida.InicioDeCorrida;
import io.github.andresdiegolanda.elparte.fuentes.arce.ArceDePrueba;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La medición contra el banco (ADR 0014) sobre tres noches de octubre del ARCE de mentira: las noches en
 * orden, sin volver a pedir lo que la noche anterior ya leyó, la salida con la forma de
 * banco-de-pruebas/formato.md, el salteo de lo ya medido, el tope de gasto y la repetición de una noche
 * del banco sin red ni costo.
 *
 * <p>La salida queda en {@code target/banco/2026-10-20-1/} para mirarla o contarla con
 * herramientas/banco_de_pruebas.py.
 */
class MedicionContraElBancoTest extends ConServicios {

    private static final LocalDate DESDE = LocalDate.parse(ArceDePrueba.PRIMERA_NOCHE_DEL_BANCO);
    private static final LocalDate HASTA = LocalDate.parse(ArceDePrueba.ULTIMA_NOCHE_DEL_BANCO);
    private static final Path CARPETA = Path.of("target", "banco");

    @Autowired
    MedicionContraElBanco medicion;
    @Autowired
    InicioDeCorrida inicio;
    @Autowired
    ComparacionDeCorridas comparacion;
    @Autowired
    ModeloDePrueba modelo;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    JsonMapper json;

    @Test
    void tresNochesEnOrdenSuSalidaElSalteoElTopeYLaRepeticion() throws Exception {
        // 1. Las tres noches, en orden, sobre la base de las pruebas.
        int pedidosAntes = ARCE.pedidos();
        int llamadasAntes = modelo.llamadas();
        MedicionContraElBanco.Resultado primera = medicion.medir(parametros(new BigDecimal("5.00")));

        assertThat(primera.corridas()).containsExactly(DESDE, DESDE.plusDays(1), HASTA);
        assertThat(primera.salteadas()).isEmpty();
        assertThat(primera.cortadaPorTope()).isFalse();
        assertThat(primera.gastadoUsd()).isEqualByComparingTo("0.024");
        assertThat(jdbc.sql("select id from parte.corridas where modo = 'banco' order by iniciada_en").query(String.class).list())
                .containsExactly("c-2026-10-14-banco", "c-2026-10-15-banco", "c-2026-10-16-banco");
        // El refutador y el analista, una vez por cada uno de los tres candidatos.
        assertThat(modelo.llamadas() - llamadasAntes).isEqualTo(6);
        // Tres feeds; la primera noche lee tres publicaciones, la segunda una y la tercera dos, porque lo
        // del día de margen ya lo leyó la noche anterior; el refutador relee las tres nuevas; y la
        // publicación que falla se intenta tres veces. Sin reusar lo de la noche anterior serían 17.
        assertThat(ARCE.pedidos() - pedidosAntes).isEqualTo(14);
        assertThat(jdbc.sql("""
                select count(*) from parte.grabaciones
                where corrida_id = 'c-2026-10-15-banco' and clave = 'arce:publicacion:adjudicacion-1100002'
                """).query(Long.class).single()).as("lo reusado se graba igual en la corrida que lo usa").isEqualTo(1);

        // 2. La salida, con la forma de banco-de-pruebas/formato.md.
        JsonNode salida = json.readTree(Files.readString(primera.salida()));
        assertThat(primera.salida()).isEqualTo(CARPETA.resolve("2026-10-20-1").resolve("salida-de-el-parte.json"));
        assertThat(salida.path("commit").asString()).isEqualTo("prueba");
        assertThat(salida.path("entidad").asString()).isEqualTo("organismo:bps");
        assertThat(texto(salida.path("fuentes"))).containsExactly("arce", "impo");
        assertThat(salida.path("modelos").path("refutador").asString()).isEqualTo("claude-sonnet-5-5");
        List<JsonNode> corridas = lista(salida.path("corridas"));
        assertThat(corridas).extracting(c -> c.path("noche").asString())
                .containsExactly("2026-10-14", "2026-10-15", "2026-10-16");
        assertThat(corridas).extracting(c -> c.path("estado").asString()).containsOnly("completa");
        assertThat(corridas).extracting(c -> c.path("costoUsd").decimalValue())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.016"), new BigDecimal("0.008"), BigDecimal.ZERO);
        assertThat(corridas).extracting(c -> c.path("lecturasFallidas").asInt()).containsExactly(0, 0, 1);
        assertThat(corridas).allSatisfy(c -> assertThat(c.path("noRepetibles").isEmpty()).isTrue());
        assertThat(lista(salida.path("hechos"))).extracting(h -> h.path("clave").asString() + " " + h.path("noche").asString()
                + " " + h.path("grado").asString()).containsExactly(
                        "arce:ocds-yfs5dr-1100001:llamado 2026-10-14 PROVEN",
                        "arce:ocds-yfs5dr-1100002:adjudicacion 2026-10-14 PROVEN",
                        "arce:ocds-yfs5dr-1100004:aclaracion 2026-10-15 INFERRED");
        // Cada hecho lleva la huella de su afirmación, la misma que calcula herramientas/banco_de_pruebas.py.
        assertThat(lista(salida.path("hechos"))).allSatisfy(h -> assertThat(h.path("contenido").asString()).matches("sha256:[0-9a-f]{16}"));
        assertThat(SalidaDelBanco.huellaDeContenido("hola")).isEqualTo("sha256:b221d9dbb083a7f3");

        // 3. Medir otra vez saltea lo ya medido y no pide nada. Una fuente que no deja pedir la fecha
        // (que ARCE nunca da, así que se agrega a mano) sale como no repetible, no como lectura fallida.
        jdbc.sql("""
                insert into parte.mensajes (mensaje_id, tipo, version, corrida_id, causa_id, agente, emitido_en, contenido)
                values ('m-prueba-sin-fecha', 'fuente.fallida', 1, 'c-2026-10-16-banco', null, 'explorador-arce', now(),
                        cast('{"fuente": "arce", "url": "https://www.comprasestatales.gub.uy/ocds/rss/2026/10", "tipoDeFalla": "sinFecha", "detalle": "prueba"}' as jsonb))
                """).update();
        pedidosAntes = ARCE.pedidos();
        MedicionContraElBanco.Resultado segunda = medicion.medir(parametros(new BigDecimal("5.00")));
        assertThat(segunda.corridas()).isEmpty();
        assertThat(segunda.salteadas()).containsExactly(DESDE, DESDE.plusDays(1), HASTA);
        assertThat(ARCE.pedidos()).isEqualTo(pedidosAntes);
        JsonNode ultima = lista(json.readTree(Files.readString(segunda.salida())).path("corridas")).getLast();
        assertThat(texto(ultima.path("noRepetibles"))).containsExactly("arce");
        assertThat(ultima.path("lecturasFallidas").asInt()).isEqualTo(1);

        // 4. El tope: con lo gastado ya en el tope, no se inicia ninguna noche más.
        MedicionContraElBanco.Resultado topada = medicion.medir(new MedicionContraElBanco.Parametros("2026-10-20-2",
                HASTA.plusDays(1), HASTA.plusDays(2), "organismo:bps", new BigDecimal("0.024"), CARPETA, "prueba"));
        assertThat(topada.cortadaPorTope()).isTrue();
        assertThat(topada.corridas()).isEmpty();
        assertThat(jdbc.sql("select count(*) from parte.corridas where modo = 'banco'").query(Long.class).single()).isEqualTo(3);

        // 5. Una noche del banco se repite sin red ni costo, con los mismos mensajes.
        pedidosAntes = ARCE.pedidos();
        int llamadas = modelo.llamadas();
        String repeticion = inicio.iniciarReproduccion("c-2026-10-15-banco");
        esperar(() -> jdbc.sql("select estado from parte.corridas where id = :id").param("id", repeticion)
                .query(String.class).single(), estado -> !"en_curso".equals(estado));
        ComparacionDeCorridas.Resultado resultado = esperar(() -> comparacion.comparar("c-2026-10-15-banco", repeticion),
                ComparacionDeCorridas.Resultado::iguales);
        assertThat(resultado.iguales()).as("diferencias: %s / %s", resultado.soloEnOriginal(), resultado.soloEnRepeticion())
                .isTrue();
        assertThat(ARCE.pedidos()).isEqualTo(pedidosAntes);
        assertThat(modelo.llamadas()).isEqualTo(llamadas);

        // 6. Una noche con errores del modelo detiene la medición sin escribir la salida, como cuando se
        // acaba el crédito de la API. La noche de setiembre de la prueba del presupuesto tiene compras del
        // BPS; con el modelo fallando no deja hechos, así que esa prueba no se entera.
        modelo.sinCredito(true);
        try {
            MedicionContraElBanco.Resultado sinCredito = medicion.medir(new MedicionContraElBanco.Parametros("2026-10-20-3",
                    LocalDate.parse(ArceDePrueba.NOCHE_DEL_PRESUPUESTO),
                    LocalDate.parse(ArceDePrueba.NOCHE_DEL_PRESUPUESTO).plusDays(1), "organismo:bps", new BigDecimal("5.00"),
                    CARPETA, "prueba"));
            assertThat(sinCredito.nocheConErrores()).isEqualTo(LocalDate.parse(ArceDePrueba.NOCHE_DEL_PRESUPUESTO));
            assertThat(sinCredito.corridas()).containsExactly(LocalDate.parse(ArceDePrueba.NOCHE_DEL_PRESUPUESTO));
            assertThat(sinCredito.salida()).isNull();
            assertThat(CARPETA.resolve("2026-10-20-3")).doesNotExist();
        } finally {
            modelo.sinCredito(false);
        }
    }

    private static MedicionContraElBanco.Parametros parametros(BigDecimal tope) {
        return new MedicionContraElBanco.Parametros("2026-10-20-1", DESDE, HASTA, "organismo:bps", tope, CARPETA, "prueba");
    }

    private static List<JsonNode> lista(JsonNode arreglo) {
        return arreglo.valueStream().toList();
    }

    private static List<String> texto(JsonNode arreglo) {
        return arreglo.valueStream().map(JsonNode::asString).toList();
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
