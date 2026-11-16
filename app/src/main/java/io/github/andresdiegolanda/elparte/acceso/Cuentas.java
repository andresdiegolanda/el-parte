package io.github.andresdiegolanda.elparte.acceso;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Las cuentas de los invitados, en {@code cuentas.usuarios}, con el rol de las cuentas: quién puede
 * entrar. Las escribe el alta de invitados ({@link AltaDeInvitados}). Son los únicos que tocan esa
 * tabla; fuera del login, la aplicación conoce a cada persona por su identificador.
 */
@Component
public class Cuentas implements UserDetailsService {

    private final JdbcClient jdbc;

    public Cuentas(@Qualifier(FuenteDeCuentas.NOMBRE) DataSource fuente) {
        this.jdbc = JdbcClient.create(fuente);
    }

    /** Los correos se comparan sin mayúsculas ni espacios de más. */
    public static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public UsuarioDeElParte loadUserByUsername(String correo) {
        return buscar(correo).orElseThrow(() -> new UsernameNotFoundException("No es un invitado"));
    }

    /** Un invitado activo, por su correo. */
    public Optional<UsuarioDeElParte> buscar(String correo) {
        return jdbc.sql("""
                select correo, cliente_id, responsable from cuentas.usuarios where correo = :correo and activo
                """)
                .param("correo", normalizar(correo))
                .query((f, n) -> new UsuarioDeElParte(f.getString(1), f.getObject(2, UUID.class), f.getBoolean(3)))
                .optional();
    }
}
