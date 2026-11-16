package io.github.andresdiegolanda.elparte.acceso;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lo que pasa después de pedir el enlace: si el correo está invitado, se le manda; si no, nada. En los
 * dos casos la persona ve la misma página, que le dice que revise su correo.
 */
@Component
public class ManejoDelEnlace implements OneTimeTokenGenerationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejoDelEnlace.class);

    private final Cuentas cuentas;
    private final EnvioDelEnlace envio;
    private final Acceso acceso;

    public ManejoDelEnlace(Cuentas cuentas, EnvioDelEnlace envio, Acceso acceso) {
        this.cuentas = cuentas;
        this.envio = envio;
        this.acceso = acceso;
    }

    @Override
    public void handle(HttpServletRequest pedido, HttpServletResponse respuesta, OneTimeToken token) throws IOException {
        if (cuentas.buscar(token.getUsername()).isPresent()) {
            String enlace = UriComponentsBuilder.fromUriString(acceso.urlPublica()).path("/login/ott")
                    .queryParam("token", token.getTokenValue()).build().toUriString();
            try {
                envio.enviar(token.getUsername(), enlace);
            } catch (RuntimeException e) {
                // El correo no se nombra en el registro.
                log.error("No se pudo mandar el enlace para entrar: {}", e.getMessage());
            }
        }
        respuesta.sendRedirect(pedido.getContextPath() + "/entrar/enviado");
    }
}
