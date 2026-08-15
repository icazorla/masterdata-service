package com.ej1.app.shared.rest.patch;

/**
 * Thrown when a JSON Patch (RFC 6902) document is malformed, references a path that
 * does not exist, or cannot be applied for any other reason. Mapped to {@code 400 Bad Request}
 * by {@code JsonPatchExceptionHandler}.
 */
public class JsonPatchException extends RuntimeException {
    public JsonPatchException(String message) {
        super(message);
    }
}
