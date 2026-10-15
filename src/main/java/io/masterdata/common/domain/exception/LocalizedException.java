package io.masterdata.common.domain.exception;

/**
 * Base de toda excepción que la capa REST traduce a un mensaje localizado.
 *
 * <p>Lleva un par {@code messageKey}/{@code args} junto al {@code message} simple
 * para que la capa REST pueda resolver un mensaje localizado (vía el {@code MessageSource}
 * de Spring) sin que quien la lanza sepa nada de i18n. El propio {@code message}
 * se mantiene como una cadena fija en inglés, orientada al desarrollador, usada para
 * los logs y como fallback cuando una clave no tiene traducción.
 */
public abstract class LocalizedException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    protected LocalizedException(String message, String messageKey, Object... args) {
        this(message, null, messageKey, args);
    }

    protected LocalizedException(String message, Throwable cause, String messageKey, Object... args) {
        super(message, cause);
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
