package io.github.andresdiegolanda.elparte.acceso;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La segunda fuente de datos, con el rol de las cuentas, que ve solo el esquema {@code cuentas}
 * (ADR 0008, ADR 0013). La de siempre, con el rol de los agentes, sigue siendo la que configura
 * Spring Boot: esta no es candidata por omisión, así que solo la recibe quien la pide por nombre.
 */
@Configuration
public class FuenteDeCuentas {

    public static final String NOMBRE = "cuentas";

    @Bean(defaultCandidate = false)
    @Qualifier(NOMBRE)
    DataSource datosDeCuentas(@Value("${spring.datasource.url}") String url,
            @Value("${elparte.cuentas.usuario:elparte_cuentas}") String usuario,
            @Value("${elparte.cuentas.clave:${BASE_CLAVE_CUENTAS:cuentas-local}}") String clave) {
        // Las clases de Spring Security nombran sus tablas sin esquema: el search_path las lleva a cuentas.
        String conEsquema = url + (url.contains("?") ? "&" : "?") + "currentSchema=cuentas";
        return DataSourceBuilder.create().url(conEsquema).username(usuario).password(clave).build();
    }
}
