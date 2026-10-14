package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "la consulta es sintácticamente válida
 * pero no tiene sentido semánticamente" (p. ej. {@code InvalidProductQueryException} para
 * {@code minPrice > maxPrice}). Permite que el manejador global de excepciones mapee
 * todas ellas a {@code 422 Unprocessable Entity} en un solo sitio en lugar de un método
 * manejador por agregado.
 *
 * <p>Ver {@link NotFoundException} para saber por qué esto también lleva un par
 * {@code messageKey}/{@code args} junto al {@code message} simple.
 */
public abstract class InvalidQueryException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    protected InvalidQueryException(String message, String messageKey, Object... args) {
        super(message);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
