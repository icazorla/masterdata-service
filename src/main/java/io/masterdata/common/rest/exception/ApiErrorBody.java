package io.masterdata.common.rest.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuerpo JSON común de todas las respuestas de error ({@code timestamp}, {@code status},
 * {@code error}, {@code message} y, si los hay, {@code errors}). Compartido por los
 * {@code @RestControllerAdvice} ({@link ApiExceptionHandler}) y por los errores que Spring
 * Security genera en la cadena de filtros, antes de llegar a Spring MVC
 * ({@link SecurityErrorHandler}), para que un 401/403 tenga el mismo formato que un 404.
 */
public final class ApiErrorBody {

    private ApiErrorBody() {
    }

    public static Map<String, Object> of(HttpStatus status, String message, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }
}
