package io.github.andresdiegolanda.elparte.acceso;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.andresdiegolanda.elparte.mensajes.Contenidos.Informe;
import io.github.andresdiegolanda.elparte.mensajes.Sobre;
import io.github.andresdiegolanda.elparte.mensajes.Tipos;
import tools.jackson.databind.json.JsonMapper;

/**
 * El aviso diario del parte (ADR 0013, T35). Escucha el mensaje {@code informes} y, a cada invitado
 * activo que quiere el aviso, le manda uno corto: la noche, cuántos hechos hay, cuántos esperan revisión,
 * si la corrida se cortó y el enlace a su página. No lleva el contenido de los hechos: el parte es la página.
 *
 * <p>Sale solo de las corridas normales, por el modo que trae el informe. Usa solo el rol de las cuentas:
 * el correo y la preferencia están en {@code cuentas.usuarios}, y cuántos hechos hay lo dice el mensaje,
 * no la base (ADR 0008).
 *
 * <p>Cada envío se anota en {@code cuentas.envios_del_parte} antes de mandarlo, uno por invitado y por
 * noche: un mensaje que llega dos veces, o una noche corrida otra vez a mano, avisa una sola vez. Si el
 * correo no sale, la anotación se borra y queda un aviso en el registro, sin el correo. Un informe de hace
 * más de un día y medio no se avisa: es de antes de prender el aviso, o de una corrida que se repite.
 *
 * <p>Viene apagado ({@code elparte.aviso.prendido}): en local lo captura Mailpit, y publicado se prende
 * cuando el responsable lo decide.
 */
@Component
public class AvisoDelParte {

    private static final Logger log = LoggerFactory.getLogger(AvisoDelParte.class);
    private static final String CONSUMIDOR = "aviso";
    private static final Duration VIEJO = Duration.ofHours(36);

    private final JdbcClient cuentas;
    private final EnvioDelAviso envio;
    private final Acceso acceso;
    private final JsonMapper json;
    private final Clock reloj;
    private final boolean prendido;

    public AvisoDelParte(@Qualifier(FuenteDeCuentas.NOMBRE) DataSource fuenteDeCuentas, EnvioDelAviso envio,
            Acceso acceso, JsonMapper json, Clock reloj,
            @Value("${elparte.aviso.prendido:false}") boolean prendido) {
        this.cuentas = JdbcClient.create(fuenteDeCuentas);
        this.envio = envio;
        this.acceso = acceso;
        this.json = json;
        this.reloj = reloj;
        this.prendido = prendido;
    }

    @KafkaListener(topics = Tipos.INFORMES, groupId = CONSUMIDOR)
    public void recibir(String valor) {
        if (!prendido) {
            return;
        }
        Sobre sobre = json.readValue(valor, Sobre.class);
        if (sobre.emitidoEn() != null && sobre.emitidoEn().isBefore(reloj.instant().minus(VIEJO))) {
            return;
        }
        Informe informe = json.treeToValue(sobre.contenido(), Informe.class);
        if (!"normal".equals(informe.modo())) {
            return;
        }
        avisar(sobre.corridaId(), informe);
    }

    private void avisar(String corridaId, Informe informe) {
        UUID cliente = UUID.fromString(informe.clienteId());
        Optional<String> correo = cuentas.sql(
                "select correo from cuentas.usuarios where cliente_id = :id and activo and aviso_diario")
                .param("id", cliente).query(String.class).optional();
        if (correo.isEmpty()) {
            return;
        }
        LocalDate noche = LocalDate.parse(informe.fechaObjetivo());
        int anotado = cuentas.sql("""
                insert into cuentas.envios_del_parte (cliente_id, fecha_objetivo, corrida_id)
                values (:id, :noche, :corrida) on conflict do nothing
                """).param("id", cliente).param("noche", noche).param("corrida", corridaId).update();
        if (anotado == 0) {
            return;
        }
        try {
            envio.enviar(correo.get(), asunto(informe), cuerpo(informe, acceso.urlPublica()));
        } catch (RuntimeException e) {
            cuentas.sql("delete from cuentas.envios_del_parte where cliente_id = :id and fecha_objetivo = :noche")
                    .param("id", cliente).param("noche", noche).update();
            // El correo no va al registro: es un dato personal.
            log.warn("El aviso de la noche {} no salió para un invitado: {}", noche, e.getMessage());
        }
    }

    static String asunto(Informe informe) {
        String cuantos = switch (informe.hechos().size()) {
            case 0 -> "sin novedades";
            case 1 -> "1 hecho";
            default -> informe.hechos().size() + " hechos";
        };
        return "El Parte de la noche del " + informe.fechaObjetivo() + ": " + cuantos;
    }

    static String cuerpo(Informe informe, String urlPublica) {
        StringBuilder texto = new StringBuilder();
        int hechos = informe.hechos().size();
        if (hechos == 0) {
            texto.append("El parte de la noche del ").append(informe.fechaObjetivo())
                    .append(" está listo: no hubo nada nuevo sobre lo que seguís.\n");
        } else {
            texto.append("El parte de la noche del ").append(informe.fechaObjetivo()).append(" está listo: ")
                    .append(hechos == 1 ? "1 hecho" : hechos + " hechos").append(" sobre lo que seguís");
            int pendientes = informe.pendientesDeRevision();
            if (pendientes > 0) {
                texto.append(", ").append(pendientes == 1 ? "1 todavía sin revisar" : pendientes + " todavía sin revisar");
            }
            texto.append(".\n");
        }
        if (informe.corridaCortada()) {
            texto.append("La corrida se cortó por su presupuesto: puede faltar algo de esa noche.\n");
        }
        texto.append("\nLeelo en ").append(urlPublica).append("/manana?noche=").append(informe.fechaObjetivo()).append("\n\n")
                .append("Lo que dice cada hecho, con su evidencia, está en la página: este aviso no lo trae.\n")
                .append("Para dejar de recibirlo, apagalo en ").append(urlPublica).append("/perfil.\n");
        return texto.toString();
    }
}
