package com.ej1.app.shared.rest.exception;

import com.ej1.app.shared.rest.patch.JsonPatchException;
import com.ej1.app.shared.rest.patch.JsonPatchTestFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates JSON Patch (RFC 6902) errors to HTTP responses.
 *
 * <p>Kept separate from {@link ApiExceptionHandler} because a JSON Patch document
 * (op/path/value/from) is pure REST/HTTP transport vocabulary that never reaches
 * any domain — unlike {@code ProductNotFoundException} or {@code DuplicateSkuException}.
 * {@link JsonPatchException} and {@link JsonPatchTestFailedException} are thrown by
 * {@link JsonPatch} / {@code JsonPatchApplier}, both in {@code shared.rest.patch}, so
 * any feature that exposes a JSON Patch endpoint gets this translation for free
 * without declaring its own handler.
 *
 * <p>{@code @Order(0)}: must sort before {@link ApiExceptionHandler}'s catch-all
 * advice ({@code @Order(LOWEST_PRECEDENCE)}) — see the note there on why advice
 * ordering matters when more than one {@code @ControllerAdvice} is registered.
 */
@RestControllerAdvice
@Order(0)
@Slf4j
@RequiredArgsConstructor
public class JsonPatchExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(JsonPatchTestFailedException.class)
    public ResponseEntity<Map<String, Object>> handleTestFailed(JsonPatchTestFailedException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT, resolve(ex, locale));
    }

    @ExceptionHandler(JsonPatchException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPatch(JsonPatchException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, resolve(ex, locale));
    }

    /**
     * {@link com.ej1.app.shared.rest.patch.JsonPatch} wraps the operation-specific exception
     * (e.g. "member 'x' does not exist") once, adding "Operation N (\"op\" \"path\")" context,
     * keeping the original as {@code getCause()}. Resolving that wrapper's message key needs the
     * inner exception's message already localized, so the inner one is resolved first and passed
     * in as the wrapper key's last placeholder.
     */
    private String resolve(JsonPatchException ex, Locale locale) {
        if (ex.getCause() instanceof JsonPatchException inner) {
            String resolvedInner = resolve(inner, locale);
            Object[] args = Arrays.copyOf(ex.getArgs(), ex.getArgs().length + 1);
            args[args.length - 1] = resolvedInner;
            return messageSource.getMessage(ex.getMessageKey(), args, ex.getMessage(), locale);
        }
        return messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), ex.getMessage(), locale);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("errors", List.of());
        return ResponseEntity.status(status).body(body);
    }
}
