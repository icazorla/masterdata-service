package com.ej1.app.common.rest.patch;

/**
 * Se lanza cuando un documento JSON Patch (RFC 6902) está malformado, referencia una ruta
 * que no existe, o no se puede aplicar por cualquier otro motivo. {@code JsonPatchExceptionHandler}
 * la mapea a {@code 400 Bad Request}.
 *
 * <p>Lleva un par {@code messageKey}/{@code args} junto al {@code message} simple para que
 * {@code JsonPatchExceptionHandler} pueda resolver un mensaje localizado vía el
 * {@code MessageSource} de Spring. Esta clase se mantiene agnóstica del framework (ver el
 * Javadoc de {@link JsonPatch}) — {@code messageKey}/{@code args} son simples campos, sin
 * ninguna dependencia de Spring aquí.
 */
public class JsonPatchException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    public JsonPatchException(String message, String messageKey, Object... args) {
        super(message);
        this.messageKey = messageKey;
        this.args = args;
    }

    public JsonPatchException(String message, Throwable cause, String messageKey, Object... args) {
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
