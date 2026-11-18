package io.github.andresdiegolanda.elparte.acceso;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * Quién entra y qué puede hacer (ADR 0013).
 *
 * <ul>
 * <li>Se entra con un enlace de correo: el token de un solo uso de Spring Security, guardado en
 * {@code cuentas.one_time_tokens}, que vence a los 5 minutos.</li>
 * <li>Al entrar queda un recordatorio de sesión por 30 días, en {@code cuentas.persistent_logins}.</li>
 * <li>Correr o repetir una corrida y la cola de revisión son solo del responsable del proyecto: una
 * corrida gasta crédito, y una revisión cambia lo que se publica. La sala de máquinas se puede mirar
 * sin entrar: muestra cómo trabaja El Parte, y en el esquema {@code parte} no hay datos personales.</li>
 * <li>La página de la mañana muestra, con sesión, el parte de quien entró; sin sesión, el de la cuenta
 * de demostración. El perfil también: sin sesión se ve el de la demostración, y cambiarlo pide entrar.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({Acceso.class, Invitados.class})
public class Seguridad {

    static final Duration RECORDATORIO = Duration.ofDays(30);

    @Bean
    JdbcOneTimeTokenService tokensGuardados(@Qualifier(FuenteDeCuentas.NOMBRE) DataSource cuentas) {
        return new JdbcOneTimeTokenService(new JdbcTemplate(cuentas));
    }

    @Bean
    PersistentTokenRepository recordatorios(@Qualifier(FuenteDeCuentas.NOMBRE) DataSource cuentas) {
        JdbcTokenRepositoryImpl recordatorios = new JdbcTokenRepositoryImpl();
        recordatorios.setDataSource(cuentas);
        return recordatorios;
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, Acceso acceso, Cuentas cuentas, JdbcOneTimeTokenService tokens,
            ManejoDelEnlace manejo, PersistentTokenRepository recordatorios) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/corridas", "/corridas/**").hasRole(UsuarioDeElParte.RESPONSABLE)
                        .requestMatchers(HttpMethod.POST, "/modelos/**").hasRole(UsuarioDeElParte.RESPONSABLE)
                        .requestMatchers(HttpMethod.POST, "/expediente/**").hasRole(UsuarioDeElParte.RESPONSABLE)
                        .requestMatchers("/revision", "/revision/**").hasRole(UsuarioDeElParte.RESPONSABLE)
                        .requestMatchers(HttpMethod.POST, "/perfil/**").authenticated()
                        .anyRequest().permitAll())
                .userDetailsService(cuentas)
                .oneTimeTokenLogin(ott -> ott
                        .tokenService(new TokensDeInvitados(tokens, cuentas))
                        .tokenGenerationSuccessHandler(manejo)
                        .loginPage("/entrar")
                        // Sin esto, la página de entrar pasaría a ser también la que procesa el token.
                        .loginProcessingUrl("/login/ott")
                        .showDefaultSubmitPage(false)
                        .defaultSuccessUrl("/manana")
                        .failureUrl("/entrar?vencido"))
                .rememberMe(r -> r
                        .tokenRepository(recordatorios)
                        .userDetailsService(cuentas)
                        .alwaysRemember(true)
                        .tokenValiditySeconds((int) RECORDATORIO.toSeconds())
                        .key(acceso.claveRecordatorio()))
                .logout(l -> l.logoutSuccessUrl("/manana"));
        return http.build();
    }
}
