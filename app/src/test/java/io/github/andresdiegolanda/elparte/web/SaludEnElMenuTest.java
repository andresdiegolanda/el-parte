package io.github.andresdiegolanda.elparte.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SaludEnElMenuTest {

    @Test
    void enLocalLaSaludEstaEnElPuertoDeLasPaginas() {
        assertTrue(new SaludEnElMenu("", "8080").saludEnLasPaginas());
        assertTrue(new SaludEnElMenu("8080", "8080").saludEnLasPaginas());
    }

    @Test
    void publicadaVaEnOtroPuertoYNoSeMuestra() {
        assertFalse(new SaludEnElMenu("8081", "8080").saludEnLasPaginas());
    }
}
