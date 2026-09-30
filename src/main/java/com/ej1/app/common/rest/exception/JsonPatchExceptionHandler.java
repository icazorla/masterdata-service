package com.ej1.app.common.rest.exception;

import com.ej1.app.common.rest.patch.JsonPatchException;
import com.ej1.app.common.rest.patch.JsonPatchTestFailedException;
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
 * Traduce los errores de JSON Patch (RFC 6902) a respuestas HTTP.
 *
 * <p>Se mantiene separado de {@link ApiExceptionHandler} porque un documento JSON
 * Patch (op/path/value/from) es vocabulario puro de transporte REST/HTTP que nunca
 * llega a ningún dominio — a diferencia de {@code ProductNotFoundException} o
 * {@code DuplicateSkuException}. {@link JsonPatchException} y {@link JsonPatchTestFailedException}
 * los lanzan {@link JsonPatch} / {@code JsonPatchApplier}, ambos en {@code shared.rest.patch},
 * de forma que cualquier feature que exponga un endpoint JSON Patch obtiene esta
 * traducción gratis sin declarar su propio manejador.
 *
 * <p>{@code @Order(0)}: debe ordenarse antes del advice general de
 * {@link ApiExceptionHandler} ({@code @Order(LOWEST_PRECEDENCE)}) — ver la nota
 * de allí sobre por qué importa el orden de los advices cuando hay registrado
 * más de un {@code @ControllerAdvice}.
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
     * {@link com.ej1.app.common.rest.patch.JsonPatch} envuelve una vez la excepción específica
     * de la operación (p. ej. "member 'x' does not exist"), añadiendo el contexto
     * "Operation N (\"op\" \"path\")", conservando la original como {@code getCause()}. Resolver
     * la clave de mensaje de ese envoltorio necesita que el mensaje de la excepción interna ya
     * esté localizado, así que la interna se resuelve primero y se pasa como el último
     * placeholder de la clave del envoltorio.
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
