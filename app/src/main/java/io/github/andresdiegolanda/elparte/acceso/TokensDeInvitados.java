package io.github.andresdiegolanda.elparte.acceso;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;

/**
 * Los tokens de un solo uso, guardados en {@code cuentas.one_time_tokens} por el servicio de Spring
 * Security, pero solo para los invitados. Para un correo que no está invitado devuelve un token que no
 * se guarda ni se manda, así la respuesta es la misma y no se puede averiguar quién tiene cuenta (ADR
 * 0013), y la tabla no se llena con pedidos de cualquiera.
 */
public class TokensDeInvitados implements OneTimeTokenService {

    private final OneTimeTokenService guardados;
    private final Cuentas cuentas;

    public TokensDeInvitados(OneTimeTokenService guardados, Cuentas cuentas) {
        this.guardados = guardados;
        this.cuentas = cuentas;
    }

    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest pedido) {
        String correo = Cuentas.normalizar(pedido.getUsername());
        if (cuentas.buscar(correo).isEmpty()) {
            return new DefaultOneTimeToken(UUID.randomUUID().toString(), correo, Instant.now());
        }
        return guardados.generate(new GenerateOneTimeTokenRequest(correo, pedido.getExpiresIn()));
    }

    @Override
    public OneTimeToken consume(OneTimeTokenAuthenticationToken token) {
        return guardados.consume(token);
    }
}
