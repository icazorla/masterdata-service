package io.masterdata.common.rest.patch;

import io.masterdata.common.domain.exception.LocalizedException;

/**
 * Se lanza cuando un documento JSON Patch (RFC 6902) está malformado, referencia una ruta
 * que no existe, o no se puede aplicar por cualquier otro motivo. {@code ApiExceptionHandler}
 * la mapea a {@code 400 Bad Request}, con su mensaje localizado (ver {@link LocalizedException}).
 */
public class JsonPatchException extends LocalizedException {

    public JsonPatchException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }

    public JsonPatchException(String message, Throwable cause, String messageKey, Object... args) {
        super(message, cause, messageKey, args);
    }
}
