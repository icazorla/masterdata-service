package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "la consulta es sintácticamente válida
 * pero no tiene sentido semánticamente" (p. ej. {@code InvalidCatalogQueryException} para
 * un {@code sortBy} no ordenable). Permite que el manejador global de excepciones mapee
 * todas ellas a {@code 422 Unprocessable Entity} en un solo sitio.
 */
public abstract class InvalidQueryException extends LocalizedException {

    protected InvalidQueryException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }
}
