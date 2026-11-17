package io.github.andresdiegolanda.elparte;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.andresdiegolanda.elparte.acceso.EnvioDelAviso;

/** El aviso diario de las pruebas (T35): guarda cada aviso en lugar de mandarlo. */
public class BuzonDeAvisos implements EnvioDelAviso {

    public record Aviso(String correo, String asunto, String cuerpo) {
    }

    private final List<Aviso> avisos = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        avisos.add(new Aviso(destinatario, asunto, cuerpo));
    }

    /** Los avisos de una noche, por el asunto, que la nombra. */
    public List<Aviso> deLaNoche(String noche) {
        return avisos.stream().filter(a -> a.asunto().contains("noche del " + noche + ":")).toList();
    }
}
