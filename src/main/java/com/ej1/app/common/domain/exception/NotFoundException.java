package com.ej1.app.common.domain.exception;

/**
 * Tipo base para las excepciones de dominio de "el recurso solicitado no existe"
 * (p. ej. {@code ProductNotFoundException}, {@code CategoryNotFoundException},
 * {@code BrandNotFoundException}). Permite que el manejador global de excepciones
 * mapee todas ellas a {@code 404 Not Found} en un solo sitio en lugar de un método
 * manejador por agregado.
 *
 * <p>Lleva un par {@code messageKey}/{@code args} junto al {@code message} simple
 * para que la capa REST pueda resolver un mensaje localizado (vía el {@code MessageSource}
 * de Spring) sin que la capa de dominio sepa nada de i18n. El propio {@code message}
 * se mantiene como una cadena fija en inglés, orientada al desarrollador, usada para
 * los logs y como fallback cuando una clave no tiene traducción.
 */
public abstract class NotFoundException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    protected NotFoundException(String message, String messageKey, Object... args) {
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
