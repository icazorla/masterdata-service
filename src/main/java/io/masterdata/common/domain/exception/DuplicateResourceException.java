package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "ya existe un recurso con esta clave
 * única" (p. ej. {@code DuplicateCatalogNameException}). Permite que el manejador global
 * de excepciones mapee todas ellas a {@code 409 Conflict} en un solo sitio.
 */
public abstract class DuplicateResourceException extends LocalizedException {

    protected DuplicateResourceException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }
}
