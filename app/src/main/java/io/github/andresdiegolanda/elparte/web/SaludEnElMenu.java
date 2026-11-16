package io.github.andresdiegolanda.elparte.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Si las páginas llevan a la salud de la aplicación ({@code /actuator/health}). En local está en el mismo
 * puerto que las páginas y el enlace se muestra. Publicada, va en otro puerto, al que el túnel no llega
 * (ADR 0016): el enlace daría error, y no se muestra.
 */
@ControllerAdvice
public class SaludEnElMenu {

    private final boolean visible;

    public SaludEnElMenu(@Value("${management.server.port:}") String puertoDeLaSalud,
            @Value("${server.port:8080}") String puertoDeLasPaginas) {
        this.visible = puertoDeLaSalud.isBlank() || puertoDeLaSalud.equals(puertoDeLasPaginas);
    }

    @ModelAttribute("saludEnLasPaginas")
    public boolean saludEnLasPaginas() {
        return visible;
    }
}
