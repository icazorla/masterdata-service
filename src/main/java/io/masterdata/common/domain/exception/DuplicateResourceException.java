package io.masterdata.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "ya existe un recurso con esta clave
 * única" (p. ej. {@code DuplicateSkuException}, {@code DuplicateCategoryNameException},
 * {@code DuplicateBrandNameException}). Permite que el manejador global de excepciones
 * mapee todas ellas a {@code 409 Conflict} en un solo sitio en lugar de un método
 * manejador por agregado.
 *
 * <p>Ver {@link NotFoundException} para saber por qué esto también lleva un par
 * {@code messageKey}/{@code args} junto al {@code message} simple.
 */
public abstract class DuplicateResourceException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    protected DuplicateResourceException(String message, String messageKey, Object... args) {
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
