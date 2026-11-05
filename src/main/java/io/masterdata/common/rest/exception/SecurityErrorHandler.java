package io.masterdata.common.rest.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 401/403 de Spring Security con el mismo cuerpo JSON y los mismos mensajes localizados que
 * el resto de errores de la API. Hace falta aparte de {@link ApiExceptionHandler} porque estos
 * errores se producen en la cadena de filtros de seguridad, antes de que la petición llegue a
 * Spring MVC (y por tanto a cualquier {@code @RestControllerAdvice}).
 *
 * <ul>
 *   <li>{@link AuthenticationEntryPoint}: sin credenciales o credenciales incorrectas → 401 con
 *       la cabecera {@code WWW-Authenticate: Basic} (Swagger UI / navegador piden usuario y contraseña).</li>
 *   <li>{@link AccessDeniedHandler}: autenticado pero sin el rol requerido → 403.</li>
 * </ul>
 *
 * <p>El idioma se resuelve con el mismo {@link LocaleResolver} de Spring MVC
 * ({@code Accept-Language}), que aquí no se ha aplicado todavía a la petición.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String REALM = "app";

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        log.warn("{} {}: {}", request.getMethod(), LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
        write(request, response, HttpStatus.UNAUTHORIZED, "error.unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        log.warn("{} {}: {} ({})", request.getMethod(), LogSanitizer.clean(request.getRequestURI()), LogSanitizer.clean(ex.getMessage()),
                request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName());
        write(request, response, HttpStatus.FORBIDDEN, "error.forbidden");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String messageKey) throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorBody.of(status, messageSource.getMessage(messageKey, null, locale), List.of()));
    }
}
