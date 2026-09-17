package com.ej1.app.shared.domain.exception;

/**
 * Base type for "a resource with this unique key already exists" domain
 * exceptions (e.g. {@code DuplicateSkuException}, {@code DuplicateCategoryNameException},
 * {@code DuplicateBrandNameException}). Lets the global exception handler map all
 * of them to {@code 409 Conflict} in one place instead of one handler method
 * per aggregate.
 *
 * <p>See {@link NotFoundException} for why this also carries a {@code messageKey}/{@code args}
 * pair alongside the plain {@code message}.
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
