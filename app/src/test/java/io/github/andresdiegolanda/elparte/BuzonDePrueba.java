package io.github.andresdiegolanda.elparte;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.andresdiegolanda.elparte.acceso.EnvioDelEnlace;

/** Un buzón de mentira: guarda los enlaces para entrar en lugar de mandarlos por correo. */
public class BuzonDePrueba implements EnvioDelEnlace {

    public record Carta(String correo, String enlace) {
    }

    private final List<Carta> cartas = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String correo, String enlace) {
        cartas.add(new Carta(correo, enlace));
    }

    public List<Carta> cartas() {
        return List.copyOf(cartas);
    }

    public Optional<String> ultimoEnlacePara(String correo) {
        return cartas.reversed().stream().filter(c -> c.correo().equalsIgnoreCase(correo)).map(Carta::enlace).findFirst();
    }
}
