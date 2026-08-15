package com.ej1.app.shared.rest.patch;

/**
 * Thrown when a JSON Patch {@code "test"} operation's value does not match the current
 * document state (RFC 6902 §4.6). Mapped to {@code 409 Conflict} by
 * {@code JsonPatchExceptionHandler} — the client's expected precondition no longer holds.
 */
public class JsonPatchTestFailedException extends JsonPatchException {
    public JsonPatchTestFailedException(String message) {
        super(message);
    }
}
