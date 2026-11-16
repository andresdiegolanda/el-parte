package io.github.andresdiegolanda.elparte.acceso;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * La lista de invitados, en {@code elparte.invitados} (ADR 0013). Va en el entorno, nunca en el
 * repositorio: {@code ELPARTE_INVITADOS_0_CORREO}, {@code ELPARTE_INVITADOS_0_ALIAS} y
 * {@code ELPARTE_INVITADOS_0_RESPONSABLE}, y así con 1, 2...
 */
@ConfigurationProperties("elparte")
public record Invitados(@DefaultValue List<Invitado> invitados) {

    /** Un invitado: su correo, cómo se lo nombra en las páginas, y si es el responsable del proyecto. */
    public record Invitado(String correo, String alias, boolean responsable) {
    }
}
