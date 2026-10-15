package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "el cliente trabaja sobre una versión que ya no es
 * la actual" (p. ej. {@code CatalogVersionConflictException}). El manejador global de excepciones
 * mapea todas ellas a {@code 412 Precondition Failed}: la precondición {@code If-Match} no se cumple.
 */
public abstract class StaleResourceException extends LocalizedException {

    protected StaleResourceException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }
}
