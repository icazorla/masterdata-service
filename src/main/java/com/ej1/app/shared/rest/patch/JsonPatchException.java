package com.ej1.app.shared.rest.patch;

/**
 * Thrown when a JSON Patch (RFC 6902) document is malformed, references a path that
 * does not exist, or cannot be applied for any other reason. Mapped to {@code 400 Bad Request}
 * by {@code JsonPatchExceptionHandler}.
 *
 * <p>Carries a {@code messageKey}/{@code args} pair alongside the plain {@code message} so
 * {@code JsonPatchExceptionHandler} can resolve a localized message via Spring's
 * {@code MessageSource}. This class stays framework-agnostic (see {@link JsonPatch}'s Javadoc)
 * — {@code messageKey}/{@code args} are just plain fields, with no Spring dependency here.
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
