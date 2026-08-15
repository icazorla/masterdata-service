package com.ej1.app.product.infrastructure.exception;

import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.InvalidProductQueryException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code @Order(LOWEST_PRECEDENCE)}: Spring does not rank {@code @ExceptionHandler} specificity
 * <em>across</em> separate {@code @ControllerAdvice} beans — for a given exception it picks the
 * first advice bean (in order) that has any matching handler, even if a later advice bean
 * (e.g. {@code JsonPatchExceptionHandler}) declares a more specific one. Since this class's
 * {@link #handleGeneric} matches every {@link Exception}, it must always sort last, or it would
 * silently swallow exceptions meant for other advices.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ProductNotFoundException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateSkuException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    /**
     * Query content well-formed per the declared media type, but semantically invalid
     * as a product query (e.g. {@code minPrice > maxPrice}) — see the RFC 10008 400 vs
     * 422 distinction documented on {@link InvalidProductQueryException}.
     */
    @ExceptionHandler(InvalidProductQueryException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQuery(InvalidProductQueryException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    /**
     * Programmatic Bean Validation failures — e.g. the result of applying a JSON Patch
     * document is re-validated manually (there is no {@code @RequestBody} to hang
     * {@code @Valid} on), unlike {@link MethodArgumentNotValidException} above.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    /**
     * Malformed or unreadable request body (e.g. invalid JSON). Spring's default resolvers
     * already map this to 400, but without an explicit handler here it would be caught by
     * {@link #handleGeneric} instead, since {@code @ExceptionHandler(Exception.class)} matches
     * before Spring's built-in {@code HandlerExceptionResolver} chain runs.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
    }

    /**
     * Unsupported {@code Content-Type} (e.g. sending {@code application/json} to an endpoint
     * that requires {@code application/json-patch+json}). See {@link #handleNotReadable} for
     * why this needs an explicit handler here rather than relying on Spring's default.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn(ex.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", List.of());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
