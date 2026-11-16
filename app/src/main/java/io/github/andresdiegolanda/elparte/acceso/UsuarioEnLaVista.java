package io.github.andresdiegolanda.elparte.acceso;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Lo que las páginas saben de quien entró: su alias, del esquema {@code parte}, y si es el responsable.
 * El correo no llega a ninguna página.
 */
@ControllerAdvice
public class UsuarioEnLaVista {

    private final JdbcClient parte;

    public UsuarioEnLaVista(JdbcClient parte) {
        this.parte = parte;
    }

    public record Vista(String alias, boolean responsable) {
    }

    @ModelAttribute("usuario")
    public Vista usuario(@AuthenticationPrincipal UsuarioDeElParte usuario) {
        if (usuario == null) {
            return null;
        }
        String alias = parte.sql("select alias from parte.clientes where cliente_id = :id")
                .param("id", usuario.clienteId()).query(String.class).optional().orElse("Invitado");
        return new Vista(alias, usuario.responsable());
    }
}
