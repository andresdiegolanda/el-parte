package io.github.andresdiegolanda.elparte.acceso;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * El alta de los invitados al arrancar (ADR 0013): cada invitado de la configuración tiene su cuenta en
 * {@code cuentas.usuarios} y su cliente en {@code parte.clientes}, con el mismo identificador. Nadie se
 * registra solo. Las cuentas se escriben con el rol de las cuentas, y los clientes, con el de los agentes.
 *
 * <p>Un invitado nuevo empieza siguiendo lo mismo que la cuenta de demostración, así su primera página
 * de la mañana no sale vacía; lo cambia en su perfil (T34). Uno que ya existía conserva lo que sigue.
 *
 * <p>Un invitado cuyo correo no tiene forma de correo, por ejemplo el texto de ejemplo que quedó en el
 * {@code .env}, no se da de alta y queda un aviso en el registro, con su número y sin el texto (T44). La
 * aplicación arranca igual: los demás invitados y la demostración siguen andando. Si ya estaba dado de
 * alta, queda desactivado, como uno que ya no está en la lista.
 */
@Component
public class AltaDeInvitados {

    private static final Logger log = LoggerFactory.getLogger(AltaDeInvitados.class);

    private final Invitados invitados;
    private final JdbcClient cuentas;
    private final JdbcClient parte;

    public AltaDeInvitados(Invitados invitados, @Qualifier(FuenteDeCuentas.NOMBRE) DataSource fuenteDeCuentas,
            JdbcClient parte) {
        this.invitados = invitados;
        this.cuentas = JdbcClient.create(fuenteDeCuentas);
        this.parte = parte;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void darDeAlta() {
        int numero = 0;
        for (Invitados.Invitado invitado : invitados.invitados()) {
            numero++;
            if (invitado.correo() == null || invitado.correo().isBlank()) {
                log.warn("El invitado {} de la configuración no tiene correo: no se da de alta", numero);
                continue;
            }
            if (!Cuentas.esCorreo(invitado.correo())) {
                // El texto no va al registro: puede ser un correo mal escrito, que es un dato personal.
                log.warn("El invitado {} de la configuración (ELPARTE_INVITADOS_{}_CORREO) no es un correo: no se da de alta",
                        numero, numero - 1);
                continue;
            }
            UUID id = cuenta(invitado.correo(), invitado.responsable());
            String alias = invitado.alias() == null || invitado.alias().isBlank() ? "Invitado " + numero : invitado.alias();
            int nuevo = parte.sql("""
                    insert into parte.clientes (cliente_id, alias, es_demo) values (:id, :alias, false)
                    on conflict (cliente_id) do nothing
                    """).param("id", id).param("alias", alias).update();
            if (nuevo == 1) {
                parte.sql("""
                        insert into parte.suscripciones (cliente_id, entidad_id)
                        select :id, s.entidad_id from parte.suscripciones s
                        join parte.clientes c on c.cliente_id = s.cliente_id
                        where c.es_demo
                        """).param("id", id).update();
            } else {
                parte.sql("update parte.clientes set alias = :alias where cliente_id = :id")
                        .param("alias", alias).param("id", id).update();
            }
        }
        int desactivados = desactivarLosQueNoEstan(invitados.invitados().stream()
                .map(Invitados.Invitado::correo).filter(Cuentas::esCorreo).toList());
        // Solo cuántos: los correos no van al registro de la aplicación.
        log.info("Invitados: {} en la configuración, {} desactivados por no estar", numero, desactivados);
    }

    /** Da de alta o actualiza la cuenta de un invitado y devuelve su identificador, que se mantiene entre arranques. */
    private UUID cuenta(String correo, boolean responsable) {
        String normal = Cuentas.normalizar(correo);
        Optional<UUID> existente = cuentas.sql("select cliente_id from cuentas.usuarios where correo = :correo")
                .param("correo", normal).query(UUID.class).optional();
        if (existente.isPresent()) {
            cuentas.sql("update cuentas.usuarios set responsable = :responsable, activo = true where cliente_id = :id")
                    .param("responsable", responsable).param("id", existente.get()).update();
            return existente.get();
        }
        UUID id = UUID.randomUUID();
        cuentas.sql("insert into cuentas.usuarios (cliente_id, correo, responsable) values (:id, :correo, :responsable)")
                .param("id", id).param("correo", normal).param("responsable", responsable).update();
        return id;
    }

    /** Los que ya no están en la lista de invitados no pueden entrar; su cuenta queda, desactivada. */
    private int desactivarLosQueNoEstan(List<String> correos) {
        List<String> normales = correos.stream().map(Cuentas::normalizar).toList();
        if (normales.isEmpty()) {
            return cuentas.sql("update cuentas.usuarios set activo = false where activo").update();
        }
        return cuentas.sql("update cuentas.usuarios set activo = false where activo and correo not in (:correos)")
                .param("correos", normales).update();
    }
}
