package io.github.andresdiegolanda.elparte.infraestructura;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Dice si Kafka responde. Spring Boot trae el indicador de salud de la base,
 * pero no el de Kafka, y sin Kafka los agentes no se pasan el trabajo (ADR 0002).
 *
 * <p>Aparece en {@code /actuator/health} como {@code saludDeKafka}.
 */
@Component
public class SaludDeKafka extends AbstractHealthIndicator {

    private static final int SEGUNDOS_DE_ESPERA = 5;

    private final KafkaAdmin kafkaAdmin;

    public SaludDeKafka(KafkaAdmin kafkaAdmin) {
        super("Kafka no respondió");
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    protected void doHealthCheck(Health.Builder salud) throws Exception {
        Map<String, Object> configuracion = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        configuracion.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, SEGUNDOS_DE_ESPERA * 1000);
        configuracion.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, SEGUNDOS_DE_ESPERA * 1000);
        try (AdminClient cliente = AdminClient.create(configuracion)) {
            DescribeClusterResult cluster = cliente.describeCluster(
                    new DescribeClusterOptions().timeoutMs(SEGUNDOS_DE_ESPERA * 1000));
            String idDelCluster = cluster.clusterId().get(SEGUNDOS_DE_ESPERA, TimeUnit.SECONDS);
            int nodos = cluster.nodes().get(SEGUNDOS_DE_ESPERA, TimeUnit.SECONDS).size();
            salud.up().withDetail("cluster", idDelCluster).withDetail("nodos", nodos);
        }
    }
}
