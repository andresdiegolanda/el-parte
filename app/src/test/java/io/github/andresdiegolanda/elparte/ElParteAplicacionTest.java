package io.github.andresdiegolanda.elparte;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.andresdiegolanda.elparte.infraestructura.SaludDeKafka;

/**
 * El esqueleto arranca contra un PostgreSQL y un Kafka de verdad, con las mismas
 * versiones que compose.yaml, y ve a Kafka sano.
 */
@SpringBootTest
@Testcontainers
class ElParteAplicacionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.4.0");

    @Autowired
    SaludDeKafka saludDeKafka;

    @Test
    void arrancaYVeAKafka() {
        Health salud = saludDeKafka.health(true);
        assertThat(salud.getStatus()).isEqualTo(Status.UP);
        Map<String, Object> detalles = salud.getDetails();
        assertThat(detalles).containsEntry("nodos", 1);
    }
}
