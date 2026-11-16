package io.github.andresdiegolanda.elparte.acceso;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * El enlace para entrar, por correo (ADR 0013). Sale por SMTP: en local lo captura Mailpit, y con el
 * despliegue, el servicio de correo real (T27).
 */
@Component
public class CorreoDelEnlace implements EnvioDelEnlace {

    private final JavaMailSender correo;
    private final Acceso acceso;

    public CorreoDelEnlace(JavaMailSender correo, Acceso acceso) {
        this.correo = correo;
        this.acceso = acceso;
    }

    @Override
    public void enviar(String destinatario, String enlace) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(acceso.remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject("Tu enlace para entrar a El Parte");
        mensaje.setText("""
                Para entrar a El Parte, abrí este enlace:

                %s

                Sirve una sola vez y vence a los 5 minutos. Si no lo pediste, no hagas nada: sin el enlace no se entra.
                """.formatted(enlace));
        correo.send(mensaje);
    }
}
