package io.github.andresdiegolanda.elparte.fuentes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * El User-Agent con que los lectores se presentan a las fuentes tiene que ser ASCII. Con una tilde, el
 * cliente HTTP de Java la manda tal cual por HTTP/2 y el Banco Mundial responde 403 a cada pedido; un
 * servidor de mentira por HTTP/1.1 no lo ve, porque ahí la tilde llega cambiada por un signo de pregunta
 * (docs/como-se-hizo/banco-mundial.md, "Qué falló"). Por eso la prueba lee el código.
 */
class AgenteDeUsuarioTest {

    private static final Pattern AGENTE = Pattern.compile("\\.header\\(\"User-Agent\",\\s*\"([^\"]*)\"");

    @Test
    void elUserAgentDeCadaLectorEsAscii() throws IOException {
        List<String> agentes;
        try (Stream<Path> archivos = Files.walk(Path.of("src/main/java"))) {
            agentes = archivos.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    Matcher m = AGENTE.matcher(Files.readString(p, StandardCharsets.UTF_8));
                    return m.results().map(r -> p.getFileName() + ": " + r.group(1)).toList().stream();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
        }
        assertThat(agentes).hasSizeGreaterThanOrEqualTo(4);
        assertThat(agentes).allSatisfy(a -> assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(a)).as(a).isTrue());
    }
}
