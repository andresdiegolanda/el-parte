package io.github.andresdiegolanda.elparte.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** La página de inicio: qué es El Parte y a dónde ir. */
@Controller
public class InicioControlador {

    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }
}
