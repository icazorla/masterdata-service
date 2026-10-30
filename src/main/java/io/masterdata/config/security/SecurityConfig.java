package io.masterdata.config.security;

import io.masterdata.common.rest.exception.SecurityErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de la API: HTTP Basic, sin estado (sin sesión ni cookies, y por tanto sin CSRF),
 * con usuarios declarados en {@code app.security.users} (ver {@link AppSecurityProperties}).
 *
 * <table>
 *   <caption>Reglas de acceso</caption>
 *   <tr><td>{@code /api/**}</td><td>pública — API de consulta, solo lectura</td></tr>
 *   <tr><td>{@code /admin/**}</td><td>rol {@code ADMIN} — API de gestión</td></tr>
 *   <tr><td>{@code /actuator/health}, {@code /actuator/info}</td><td>públicos (los detalles de
 *       health solo para {@code ADMIN}, ver {@code management.endpoint.health})</td></tr>
 *   <tr><td>resto de {@code /actuator/**}</td><td>rol {@code ADMIN}</td></tr>
 *   <tr><td>Swagger UI / OpenAPI</td><td>públicos (las operaciones de gestión piden credenciales al probarlas)</td></tr>
 *   <tr><td>cualquier otra ruta</td><td>autenticada</td></tr>
 * </table>
 *
 * <p>Los 401/403 se devuelven con el formato de error común de la API ({@link SecurityErrorHandler}).
 */
@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    public static final String ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/admin/**").hasRole(ADMIN)
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(ADMIN)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // UI Angular empaquetada con el perfil Maven "ui": ficheros estáticos en la raíz, sin datos
                        // (los datos van por /admin/**, que sigue protegido).
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/*.js", "/*.css").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * Usuarios en memoria a partir de {@code app.security.users}. Las contraseñas llevan el prefijo
     * del algoritmo ({@code {bcrypt}}, ...) y las comprueba el {@code DelegatingPasswordEncoder}
     * por defecto de Spring Security. Declarar este bean desactiva el usuario {@code user} con
     * contraseña aleatoria que Spring Boot crearía si no.
     */
    @Bean
    public UserDetailsService userDetailsService(AppSecurityProperties properties) {
        return new InMemoryUserDetailsManager(properties.users().entrySet().stream()
                .map(entry -> User.withUsername(entry.getKey())
                        .password(entry.getValue().password())
                        .roles(entry.getValue().roles().toArray(String[]::new))
                        .build())
                .toList());
    }
}
