package io.github.andresdiegolanda.elparte.acceso;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * La configuración del acceso (ADR 0013), que usan el login y el correo. La lista de invitados está aparte,
 * en {@link Invitados}.
 *
 * @param urlPublica la dirección con la que se arma el enlace del correo, sin barra al final
 * @param remitente quién manda el correo con el enlace
 * @param claveRecordatorio la clave del recordatorio de sesión
 */
@ConfigurationProperties("elparte.acceso")
public record Acceso(
        @DefaultValue("http://localhost:8080") String urlPublica,
        @DefaultValue("El Parte <parte@localhost>") String remitente,
        @DefaultValue("el-parte-local") String claveRecordatorio) {
}
