package io.github.andresdiegolanda.elparte.infraestructura;

import java.time.Clock;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import io.github.andresdiegolanda.elparte.mensajes.Tipos;

/**
 * Tópicos, reloj y manejo de errores de los consumidores.
 *
 * <p>Cada tópico tiene una sola partición: Kafka mantiene el orden dentro de una partición, así que
 * todos los mensajes de una corrida se leen en el orden en que se escribieron (docs/contratos.md).
 * Y no vencen: el tópico es la historia de las corridas (ADR 0003, "Retención de los mensajes").
 */
@Configuration
public class ConfiguracionGeneral {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    KafkaAdmin.NewTopics topicos() {
        return new KafkaAdmin.NewTopics(Tipos.TODOS.stream()
                .map(tipo -> TopicBuilder.name(tipo).partitions(1).replicas(1)
                        .config(TopicConfig.RETENTION_MS_CONFIG, "-1")
                        .build())
                .toArray(NewTopic[]::new));
    }

    /**
     * Si un agente falla con un mensaje, se reintenta dos veces con un segundo de espera y después se
     * deja pasar. El candidato queda sin procesar y la corrida lo cuenta al cerrar por tiempo.
     */
    @Bean
    DefaultErrorHandler manejoDeErrores() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
    }
}
