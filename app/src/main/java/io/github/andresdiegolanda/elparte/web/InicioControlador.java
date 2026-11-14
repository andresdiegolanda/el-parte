package io.github.andresdiegolanda.elparte.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página de inicio. Por ahora solo confirma que la aplicación está levantada;
 * la página de la mañana y la sala de máquinas llegan con la primera rebanada (T08).
 */
@Controller
public class InicioControlador {

    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }
}
