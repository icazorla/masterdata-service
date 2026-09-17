package com.ej1.app.shared.rest.exception;

import com.ej1.app.shared.domain.exception.DuplicateResourceException;
import com.ej1.app.shared.domain.exception.InvalidQueryException;
import com.ej1.app.shared.domain.exception.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global REST exception handler, shared across every feature (product, category,
 * brand, ...). {@link NotFoundException}/{@link DuplicateResourceException}/
 * {@link InvalidQueryException} are base types each aggregate's own domain
 * exception extends (e.g. {@code ProductNotFoundException}, {@code CategoryNotFoundException},
 * {@code InvalidProductQueryException}), so a new feature gets 404/409/422 mapping
 * for free without a new handler method here.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)}: Spring does not rank {@code @ExceptionHandler}
 * specificity <em>across</em> separate {@code @ControllerAdvice} beans — for a given
 * exception it picks the first advice bean (in order) that has any matching handler,
 * even if a later advice bean (e.g. {@code JsonPatchExceptionHandler}) declares a more
 * specific one. Since this class's {@link #handleGeneric} matches every
 * {@link Exception}, it must always sort last, or it would silently swallow exceptions
 * meant for other advices.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.NOT_FOUND, resolve(ex.getMessageKey(), ex.getArgs(), ex.getMessage(), locale), List.of());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT, resolve(ex.getMessageKey(), ex.getArgs(), ex.getMessage(), locale), List.of());
    }

    /**
     * Query content well-formed per the declared media type, but semantically invalid
     * as a query (e.g. {@code minPrice > maxPrice}) — see the RFC 10008 400 vs 422
     * distinction documented on {@link InvalidQueryException}.
     */
    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQuery(InvalidQueryException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, resolve(ex.getMessageKey(), ex.getArgs(), ex.getMessage(), locale), List.of());
    }

    /**
     * A row can't be deleted because another table still references it via foreign
     * key (e.g. deleting a {@code Category}/{@code Brand} that products still point
     * to), or a race lost a unique-constraint check performed earlier in the request.
     * Surfaced by the database, wrapped by Spring Data as this exception.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT,
                messageSource.getMessage("error.dataIntegrityViolation", null, locale), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.validationFailed", null, locale), errors);
    }

    /**
     * Programmatic Bean Validation failures — e.g. the result of applying a JSON Patch
     * document is re-validated manually (there is no {@code @RequestBody} to hang
     * {@code @Valid} on), unlike {@link MethodArgumentNotValidException} above.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, Locale locale) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.validationFailed", null, locale), errors);
    }

    /**
     * Malformed or unreadable request body (e.g. invalid JSON). Spring's default resolvers
     * already map this to 400, but without an explicit handler here it would be caught by
     * {@link #handleGeneric} instead, since {@code @ExceptionHandler(Exception.class)} matches
     * before Spring's built-in {@code HandlerExceptionResolver} chain runs.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.malformedRequestBody", null, locale), List.of());
    }

    /**
     * Unsupported {@code Content-Type} (e.g. sending {@code application/json} to an endpoint
     * that requires {@code application/json-patch+json}). See {@link #handleNotReadable} for
     * why this needs an explicit handler here rather than relying on Spring's default.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, messageSource.getMessage("error.unsupportedMediaType", null, locale), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, Locale locale) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, messageSource.getMessage("error.internalServerError", null, locale), List.of());
    }

    private String resolve(String messageKey, Object[] args, String fallback, Locale locale) {
        return messageSource.getMessage(messageKey, args, fallback, locale);
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
