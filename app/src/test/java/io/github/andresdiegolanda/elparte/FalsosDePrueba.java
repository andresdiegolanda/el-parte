package io.github.andresdiegolanda.elparte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** El modelo de mentira, en lugar del de Anthropic, para todas las pruebas que heredan de {@link ConServicios}. */
@TestConfiguration
public class FalsosDePrueba {

    @Bean
    @Primary
    ModeloDePrueba modeloDePrueba() {
        return new ModeloDePrueba();
    }
}
