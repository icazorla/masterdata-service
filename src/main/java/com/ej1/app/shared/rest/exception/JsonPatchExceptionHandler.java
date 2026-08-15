package com.ej1.app.shared.rest.exception;

import com.ej1.app.shared.rest.patch.JsonPatchException;
import com.ej1.app.shared.rest.patch.JsonPatchTestFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates JSON Patch (RFC 6902) errors to HTTP responses.
 *
 * <p>Lives in {@code com.ej1.app.shared.rest.exception}, separate from any
 * feature's own {@code infrastructure.exception} advice (e.g.
 * {@code product.infrastructure.exception.ApiExceptionHandler}), because a JSON
 * Patch document (op/path/value/from) is pure REST/HTTP transport vocabulary that
 * never reaches any domain — unlike {@code ProductNotFoundException} or
 * {@code DuplicateSkuException}. {@link JsonPatchException} and
 * {@link JsonPatchTestFailedException} are thrown by {@link JsonPatch} /
 * {@code JsonPatchApplier}, both in {@code shared.rest.patch}, so any feature that
 * exposes a JSON Patch endpoint gets this translation for free without declaring
 * its own handler.
 *
 * <p>{@code @Order(0)}: must sort before a feature's own catch-all advice (typically
 * {@code @Order(LOWEST_PRECEDENCE)}) — see the note there on why advice ordering
 * matters when more than one {@code @ControllerAdvice} is registered.
 */
@RestControllerAdvice
@Order(0)
@Slf4j
public class JsonPatchExceptionHandler {

    @ExceptionHandler(JsonPatchTestFailedException.class)
    public ResponseEntity<Map<String, Object>> handleTestFailed(JsonPatchTestFailedException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(JsonPatchException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPatch(JsonPatchException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
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
