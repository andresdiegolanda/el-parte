package io.github.andresdiegolanda.elparte.acceso;

/** Cómo sale el aviso diario del parte (T35): por correo, o al buzón de las pruebas. */
public interface EnvioDelAviso {

    void enviar(String destinatario, String asunto, String cuerpo);
}
