package io.github.andresdiegolanda.elparte.acceso;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** El aviso diario por SMTP, con el mismo remitente que el enlace para entrar (ADR 0016). */
@Component
public class CorreoDelAviso implements EnvioDelAviso {

    private final JavaMailSender correo;
    private final Acceso acceso;

    public CorreoDelAviso(JavaMailSender correo, Acceso acceso) {
        this.correo = correo;
        this.acceso = acceso;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(acceso.remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        correo.send(mensaje);
    }
}
