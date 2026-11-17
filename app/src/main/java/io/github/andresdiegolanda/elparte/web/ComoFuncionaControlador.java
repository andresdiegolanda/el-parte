package io.github.andresdiegolanda.elparte.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La página "Cómo funciona" (T26): qué hace El Parte cada noche, los grados, el presupuesto y la
 * medición, con enlaces a la documentación del repositorio y al tablero de arquitectura publicado.
 */
@Controller
public class ComoFuncionaControlador {

    @GetMapping("/como-funciona")
    public String comoFunciona() {
        return "como-funciona";
    }
}
