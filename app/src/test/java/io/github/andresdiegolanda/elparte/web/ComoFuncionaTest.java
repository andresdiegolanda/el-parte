package io.github.andresdiegolanda.elparte.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import io.github.andresdiegolanda.elparte.ConServicios;
import io.github.andresdiegolanda.elparte.Navegador;

/** La página "Cómo funciona" (T26): se ve sin entrar, está en el menú y enlaza cada página "cómo se hizo". */
class ComoFuncionaTest extends ConServicios {

    @Value("${local.server.port}")
    int puerto;

    @Test
    void explicaLaNocheYEnlazaCadaPaginaComoSeHizo() throws IOException {
        Navegador anonimo = new Navegador(puerto);
        String pagina = anonimo.get("/como-funciona").body();
        assertThat(pagina).contains("El refutador", "PROVEN", "INFERRED", "NOT_MENTIONED", "/modelos", "/corridas",
                "tablero-de-arquitectura/", "docs/como-se-hizo/README.md");
        assertThat(anonimo.get("/").body()).contains("href=\"/como-funciona\"", "/marca/el-parte-lockup-color.svg");
        // El logo se sirve sin entrar: el ícono del menú, el de la pestaña y el logo del inicio.
        for (String archivo : new String[] {"/favicon.ico", "/marca/el-parte-icon-color.svg", "/marca/el-parte-lockup-color.svg"}) {
            assertThat(anonimo.get(archivo).statusCode()).as(archivo).isEqualTo(200);
        }

        // Cada página de docs/como-se-hizo/ está enlazada, y cada enlace lleva a una que existe.
        Path carpeta = Path.of("..", "docs", "como-se-hizo");
        if (Files.isDirectory(carpeta)) {
            List<String> paginas;
            try (Stream<Path> archivos = Files.list(carpeta)) {
                paginas = archivos.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".md")).sorted().toList();
            }
            assertThat(paginas).isNotEmpty().allSatisfy(n -> assertThat(pagina).contains("docs/como-se-hizo/" + n));
            java.util.regex.Matcher enlace = java.util.regex.Pattern.compile("docs/como-se-hizo/([a-z-]+\\.md)").matcher(pagina);
            while (enlace.find()) {
                assertThat(paginas).contains(enlace.group(1));
            }
        }
    }
}
