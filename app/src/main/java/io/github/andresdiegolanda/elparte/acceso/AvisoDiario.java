package io.github.andresdiegolanda.elparte.acceso;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Si cada invitado quiere el aviso diario por correo (ADR 0013, T35), en {@code cuentas.usuarios}, con el
 * rol de las cuentas. Se busca por el identificador del cliente: no lee ni devuelve correos.
 */
@Component
public class AvisoDiario {

    private final JdbcClient cuentas;

    public AvisoDiario(@Qualifier(FuenteDeCuentas.NOMBRE) DataSource fuente) {
        this.cuentas = JdbcClient.create(fuente);
    }

    public boolean quiere(UUID clienteId) {
        return cuentas.sql("select aviso_diario from cuentas.usuarios where cliente_id = :id")
                .param("id", clienteId).query(Boolean.class).optional().orElse(false);
    }

    public void cambiar(UUID clienteId, boolean quiere) {
        cuentas.sql("update cuentas.usuarios set aviso_diario = :quiere where cliente_id = :id")
                .param("quiere", quiere).param("id", clienteId).update();
    }
}
