package io.github.andresdiegolanda.elparte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * El Parte: agentes orquestados que leen fuentes públicas del Estado uruguayo
 * y publican hechos con grado de evidencia (arranque/proyecto.md).
 *
 * <p>En la versión 0 todos los agentes corren en esta aplicación, cada uno con
 * su consumidor de Kafka; la frontera entre ellos es el tópico (ADR 0002).
 */
@SpringBootApplication
public class ElParteAplicacion {

    public static void main(String[] args) {
        SpringApplication.run(ElParteAplicacion.class, args);
    }
}
