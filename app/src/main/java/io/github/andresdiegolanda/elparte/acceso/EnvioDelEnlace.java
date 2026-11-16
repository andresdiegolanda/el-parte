package io.github.andresdiegolanda.elparte.acceso;

/** Cómo le llega a una persona el enlace para entrar. En la aplicación es un correo; en las pruebas, no. */
public interface EnvioDelEnlace {

    void enviar(String correo, String enlace);
}
