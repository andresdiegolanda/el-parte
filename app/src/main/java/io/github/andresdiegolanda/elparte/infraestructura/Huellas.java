package io.github.andresdiegolanda.elparte.infraestructura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huellas SHA-256, para identificadores deterministas y para la huella del contenido leído. */
public final class Huellas {

    private Huellas() {
    }

    public static String sha256(String texto) {
        return sha256(texto.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    /**
     * El identificador de un mensaje sale de la corrida, el agente, el tipo, la causa y la posición
     * del mensaje entre los que produce esa causa (docs/contratos.md, "Idempotencia"). Procesar dos
     * veces la misma causa produce los mismos identificadores.
     */
    public static String mensajeId(String corridaId, String agente, String tipo, String causaId, int posicion) {
        return "m-" + sha256(corridaId + "|" + agente + "|" + tipo + "|" + causaId + "|" + posicion).substring(0, 20);
    }
}
