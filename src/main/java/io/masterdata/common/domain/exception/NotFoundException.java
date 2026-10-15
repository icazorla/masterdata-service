package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "el recurso solicitado no existe"
 * (p. ej. {@code CatalogNotFoundException}). Permite que el manejador global de
 * excepciones mapee todas ellas a {@code 404 Not Found} en un solo sitio.
 */
public abstract class NotFoundException extends LocalizedException {

    protected NotFoundException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }
}
