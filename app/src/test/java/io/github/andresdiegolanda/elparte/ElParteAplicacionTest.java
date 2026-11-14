package io.github.andresdiegolanda.elparte;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.common.config.ConfigResource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import io.github.andresdiegolanda.elparte.infraestructura.SaludDeKafka;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/** La aplicación arranca contra un PostgreSQL y un Kafka de verdad, ve a Kafka sano y crea sus tópicos. */
class ElParteAplicacionTest extends ConServicios {

    @Autowired
    SaludDeKafka saludDeKafka;

    @Test
    void arrancaYVeAKafka() {
        Health salud = saludDeKafka.health(true);
        assertThat(salud.getStatus()).isEqualTo(Status.UP);
        Map<String, Object> detalles = salud.getDetails();
        assertThat(detalles).containsEntry("nodos", 1);
    }

    @Test
    void creaUnTopicoPorTipoDeMensajeSinVencimiento() throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get()).containsAll(Tipos.TODOS);
            ConfigResource topico = new ConfigResource(ConfigResource.Type.TOPIC, Tipos.CANDIDATOS);
            Config configuracion = admin.describeConfigs(List.of(topico)).all().get().get(topico);
            assertThat(configuracion.get("retention.ms").value()).isEqualTo("-1");
            assertThat(admin.describeTopics(List.of(Tipos.CANDIDATOS)).allTopicNames().get()
                    .get(Tipos.CANDIDATOS).partitions()).hasSize(1);
        }
    }
}
