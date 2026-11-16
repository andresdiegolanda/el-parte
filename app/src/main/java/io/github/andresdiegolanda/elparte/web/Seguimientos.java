package io.github.andresdiegolanda.elparte.web;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Seguir y dejar de seguir una entidad del catálogo, en {@code parte.suscripciones} (ADR 0013). Vale desde
 * la corrida siguiente, que arma al empezar la lista de entidades seguidas.
 */
@Component
public class Seguimientos {

    private final JdbcClient parte;

    public Seguimientos(JdbcClient parte) {
        this.parte = parte;
    }

    /** Sigue una entidad del catálogo. Una que no está en el catálogo no se puede seguir: no hace nada. */
    public boolean seguir(UUID cliente, String entidad) {
        return parte.sql("""
                insert into parte.suscripciones (cliente_id, entidad_id)
                select :cliente, e.id from parte.entidades e where e.id = :entidad and e.identificadores -> 'arce' is not null
                on conflict do nothing
                """).param("cliente", cliente).param("entidad", entidad).update() == 1;
    }

    public boolean dejar(UUID cliente, String entidad) {
        return parte.sql("delete from parte.suscripciones where cliente_id = :cliente and entidad_id = :entidad")
                .param("cliente", cliente).param("entidad", entidad).update() == 1;
    }
}
