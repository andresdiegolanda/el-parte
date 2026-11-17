package io.github.andresdiegolanda.elparte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * El modelo de mentira, en lugar del de Anthropic, y dos buzones de mentira en lugar del correo, uno para
 * el enlace y otro para el aviso diario, para todas las pruebas que heredan de {@link ConServicios}.
 */
@TestConfiguration
public class FalsosDePrueba {

    @Bean
    @Primary
    ModeloDePrueba modeloDePrueba() {
        return new ModeloDePrueba();
    }

    @Bean
    @Primary
    BuzonDePrueba buzonDePrueba() {
        return new BuzonDePrueba();
    }

    @Bean
    @Primary
    BuzonDeAvisos buzonDeAvisos() {
        return new BuzonDeAvisos();
    }
}
