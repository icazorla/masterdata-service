package io.masterdata.common.rest.exception;

import io.masterdata.common.domain.exception.DuplicateResourceException;
import io.masterdata.common.domain.exception.InvalidQueryException;
import io.masterdata.common.domain.exception.LocalizedException;
import io.masterdata.common.domain.exception.NotFoundException;
import io.masterdata.common.rest.patch.JsonPatchException;
import io.masterdata.common.rest.patch.JsonPatchTestFailedException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones REST. {@link NotFoundException}/{@link DuplicateResourceException}/
 * {@link InvalidQueryException} son tipos base que extiende cada excepción de dominio
 * (p. ej. {@code CatalogNotFoundException}), de forma que una excepción nueva obtiene el
 * mapeo 404/409/422 gratis sin necesidad de un nuevo método manejador aquí. También traduce
 * los errores de JSON Patch (RFC 6902): {@link JsonPatchException} → 400 y
 * {@link JsonPatchTestFailedException} → 409.
 *
 * <p>Es el único {@code @ControllerAdvice}: dentro de un mismo advice Spring elige siempre el
 * manejador más específico, así que {@link #handleGeneric} solo recoge lo que no tenga otro.
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, Locale locale) {
        return localized(HttpStatus.NOT_FOUND, ex, locale);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex, Locale locale) {
        return localized(HttpStatus.CONFLICT, ex, locale);
    }

    /**
     * Contenido de la consulta bien formado según el media type declarado, pero
     * semánticamente inválido como consulta (p. ej. {@code minPrice > maxPrice}) — ver
     * la distinción entre 400 y 422 de la RFC 10008 documentada en {@link InvalidQueryException}.
     */
    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQuery(InvalidQueryException ex, Locale locale) {
        return localized(HttpStatus.UNPROCESSABLE_ENTITY, ex, locale);
    }

    /** La operación {@code test} de un JSON Patch no coincide: la precondición del cliente ya no se cumple. */
    @ExceptionHandler(JsonPatchTestFailedException.class)
    public ResponseEntity<Map<String, Object>> handleJsonPatchTestFailed(JsonPatchTestFailedException ex, Locale locale) {
        return localized(HttpStatus.CONFLICT, ex, locale);
    }

    @ExceptionHandler(JsonPatchException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJsonPatch(JsonPatchException ex, Locale locale) {
        return localized(HttpStatus.BAD_REQUEST, ex, locale);
    }

    /**
     * La base de datos rechaza la escritura por una restricción: p. ej. dos peticiones
     * concurrentes pasan a la vez la comprobación de nombre único y la segunda choca con la
     * restricción {@code (locale, name)} de la tabla de traducciones. Lo expone
     * la base de datos y lo envuelve Spring Data como esta excepción.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.CONFLICT,
                messageSource.getMessage("error.dataIntegrityViolation", null, locale), List.of());
    }

    /**
     * El valor de un parámetro de ruta/consulta no se puede convertir a su tipo
     * declarado — p. ej. {@code GET /api/catalogs/widget} ({@code widget} no es un
     * {@link io.masterdata.common.catalog.domain.CatalogType}). Los resolvers por defecto
     * de Spring ya mapean esto a 400, pero sin un manejador explícito aquí lo capturaría
     * {@link #handleGeneric} en su lugar — ver {@link #handleNotReadable}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("error.invalidPathVariable", new Object[]{ex.getValue(), ex.getName()}, locale),
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.validationFailed", null, locale), errors);
    }

    /**
     * Fallos de Bean Validation programáticos — p. ej. el resultado de aplicar un
     * documento JSON Patch se revalida manualmente (no hay un {@code @RequestBody}
     * del que colgar {@code @Valid}), a diferencia del {@link MethodArgumentNotValidException}
     * de arriba.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, Locale locale) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.validationFailed", null, locale), errors);
    }

    /**
     * Cuerpo de la petición malformado o ilegible (p. ej. JSON inválido). Los resolvers
     * por defecto de Spring ya mapean esto a 400, pero sin un manejador explícito aquí lo
     * capturaría {@link #handleGeneric} en su lugar, ya que {@code @ExceptionHandler(Exception.class)}
     * coincide antes de que se ejecute la cadena {@code HandlerExceptionResolver} propia de Spring.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, messageSource.getMessage("error.malformedRequestBody", null, locale), List.of());
    }

    /**
     * {@code Content-Type} no soportado (p. ej. enviar {@code application/json} a un endpoint
     * que requiere {@code application/json-patch+json}). Ver {@link #handleNotReadable} para
     * saber por qué esto necesita un manejador explícito aquí en lugar de confiar en el
     * comportamiento por defecto de Spring.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, messageSource.getMessage("error.unsupportedMediaType", null, locale), List.of());
    }

    /**
     * Método HTTP no soportado en una ruta que sí existe — p. ej. un {@code POST} a la API de
     * consulta ({@code /api/catalogs/{type}}, solo lectura) en vez de a la de gestión
     * ({@code /admin/api/catalogs/{type}}). 405 con la cabecera {@code Allow}; ver
     * {@link #handleNotReadable} para el porqué del manejador explícito.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, Locale locale) {
        log.warn(ex.getMessage());
        ResponseEntity<Map<String, Object>> response = build(HttpStatus.METHOD_NOT_ALLOWED,
                messageSource.getMessage("error.methodNotAllowed", new Object[]{ex.getMethod()}, locale), List.of());
        return ex.getSupportedHttpMethods() == null
                ? response
                : ResponseEntity.status(response.getStatusCode()).allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new)).body(response.getBody());
    }

    /** Ninguna ruta coincide (p. ej. el antiguo {@code /api/catalogs/{type}/{id}/translations}): 404, no 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(HttpStatus.NOT_FOUND, messageSource.getMessage("error.resourceNotFound", null, locale), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, Locale locale) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, messageSource.getMessage("error.internalServerError", null, locale), List.of());
    }

    private ResponseEntity<Map<String, Object>> localized(HttpStatus status, LocalizedException ex, Locale locale) {
        log.warn(ex.getMessage());
        return build(status, resolve(ex, locale), List.of());
    }

    /**
     * Si {@code ex} envuelve otra {@link LocalizedException} (p. ej. {@code JsonPatch} añade el
     * contexto "Operation N" a la excepción de la operación), la interna se resuelve primero y
     * se pasa como el último placeholder de la clave del envoltorio.
     */
    private String resolve(LocalizedException ex, Locale locale) {
        Object[] args = ex.getArgs();
        if (ex.getCause() instanceof LocalizedException inner) {
            args = Arrays.copyOf(args, args.length + 1);
            args[args.length - 1] = resolve(inner, locale);
        }
        return messageSource.getMessage(ex.getMessageKey(), args, ex.getMessage(), locale);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(ApiErrorBody.of(status, message, errors));
    }
}
