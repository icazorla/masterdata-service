package com.ej1.app.common.rest.patch;

/**
 * Se lanza cuando el valor de una operación {@code "test"} de JSON Patch no coincide con
 * el estado actual del documento (RFC 6902 §4.6). {@code JsonPatchExceptionHandler} la
 * mapea a {@code 409 Conflict} — la precondición que esperaba el cliente ya no se cumple.
 */
public class JsonPatchTestFailedException extends JsonPatchException {

    public JsonPatchTestFailedException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }

    public JsonPatchTestFailedException(String message, Throwable cause, String messageKey, Object... args) {
        super(message, cause, messageKey, args);
    }
}
