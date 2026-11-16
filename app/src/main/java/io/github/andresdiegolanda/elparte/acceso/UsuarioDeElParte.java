package io.github.andresdiegolanda.elparte.acceso;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Quien entró. El nombre de usuario es el correo, porque así lo guardan las tablas de Spring Security,
 * que están en el esquema {@code cuentas}. Las páginas usan solo el identificador del cliente, que es
 * el mismo en los dos esquemas: con él leen el esquema {@code parte}, sin ver el correo.
 */
public record UsuarioDeElParte(String correo, UUID clienteId, boolean responsable) implements UserDetails {

    public static final String RESPONSABLE = "RESPONSABLE";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return responsable ? List.of(new SimpleGrantedAuthority("ROLE_USUARIO"),
                new SimpleGrantedAuthority("ROLE_" + RESPONSABLE)) : List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));
    }

    /** No hay contraseña: se entra con el enlace del correo. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return correo;
    }
}
