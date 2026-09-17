package com.ej1.app.shared.domain.exception;

/**
 * Base type for "the requested resource does not exist" domain exceptions
 * (e.g. {@code ProductNotFoundException}, {@code CategoryNotFoundException},
 * {@code BrandNotFoundException}). Lets the global exception handler map all
 * of them to {@code 404 Not Found} in one place instead of one handler method
 * per aggregate.
 *
 * <p>Carries a {@code messageKey}/{@code args} pair alongside the plain {@code message}
 * so the REST layer can resolve a localized message (via Spring's {@code MessageSource})
 * without the domain layer knowing anything about i18n. {@code message} itself stays a
 * fixed, English, developer-facing string used for logs and as the fallback when a key
 * has no translation.
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
