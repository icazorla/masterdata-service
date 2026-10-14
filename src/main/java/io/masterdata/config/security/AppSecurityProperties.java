package io.masterdata.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Vincula {@code app.security.users.<username>} desde {@code application.yml}: los usuarios
 * con los que se autentica la API (HTTP Basic), cargados en memoria por {@link SecurityConfig}.
 *
 * <p>{@code password} lleva el prefijo del algoritmo de {@code DelegatingPasswordEncoder}
 * — {@code {bcrypt}...} (recomendado) o {@code {noop}...} (texto plano, solo para pruebas) —.
 * Cada campo se puede sobrescribir por variable de entorno, p. ej.
 * {@code APP_SECURITY_USERS_ADMIN_PASSWORD} para el usuario {@code admin}.
 *
 * <p>{@code roles}: sin el prefijo {@code ROLE_} (p. ej. {@code ADMIN}, ver {@link SecurityConfig#ADMIN}).
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(Map<String, UserEntry> users) {

    public AppSecurityProperties {
        users = users == null ? Map.of() : users;
    }

    public record UserEntry(String password, List<String> roles) {

        public UserEntry {
            roles = roles == null ? List.of() : roles;
        }
    }
}
