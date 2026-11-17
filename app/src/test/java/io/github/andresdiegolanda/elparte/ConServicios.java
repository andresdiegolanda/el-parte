package io.github.andresdiegolanda.elparte;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.andresdiegolanda.elparte.fuentes.arce.ArceDePrueba;
import io.github.andresdiegolanda.elparte.fuentes.impo.ImpoDePrueba;

/**
 * Lo que comparten las pruebas de la aplicación entera: un PostgreSQL y un Kafka de verdad, con las
 * mismas versiones que compose.yaml, un ARCE de mentira y un modelo de mentira. Todas las pruebas que
 * heredan de acá usan el mismo contexto de Spring, así hay un solo juego de agentes escuchando.
 *
 * <p>La aplicación se conecta con el rol de los agentes, como en compose.yaml, y las migraciones
 * corren con el dueño de la base: si a un agente le falta un permiso, las pruebas lo ven.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"elparte.corrida.revisar-cierre-ms=300", "elparte.banco.espera-ms=300",
                "elparte.invitados[0].correo=" + ConServicios.RESPONSABLE, "elparte.invitados[0].alias=Responsable",
                "elparte.invitados[0].responsable=true",
                "elparte.invitados[1].correo=" + ConServicios.INVITADA, "elparte.invitados[1].alias=Invitada uno",
                "elparte.invitados[2].correo=" + ConServicios.NO_ES_UN_CORREO, "elparte.invitados[2].alias=Sin correo",
                "elparte.aviso.prendido=true"})
@Import(FalsosDePrueba.class)
public abstract class ConServicios {

    /** Los invitados de las pruebas: correos de un dominio reservado para pruebas, que no existe. */
    public static final String RESPONSABLE = "responsable@prueba.test";
    public static final String INVITADA = "Invitada.Uno@Prueba.test";
    /** Un invitado con el texto de ejemplo en lugar del correo, como el que quedó una vez en el .env (T44). */
    public static final String NO_ES_UN_CORREO = "correo-de-la-primera-persona";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7");
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");
    protected static final ArceDePrueba ARCE = new ArceDePrueba();
    protected static final ImpoDePrueba IMPO = new ImpoDePrueba();

    static {
        POSTGRES.start();
        KAFKA.start();
        ARCE.arrancar();
    }

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.flyway.user", POSTGRES::getUsername);
        propiedades.add("spring.flyway.password", POSTGRES::getPassword);
        propiedades.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        propiedades.add("elparte.arce.url-base", ARCE::urlBase);
        propiedades.add("elparte.impo.url-base", IMPO::urlBase);
        propiedades.add("elparte.impo.pausa-ms", () -> "0");
    }
}
