package com.ej1.app.shared.domain.exception;

/**
 * Base type for "the query is syntactically valid but semantically nonsensical"
 * domain exceptions (e.g. {@code InvalidProductQueryException} for
 * {@code minPrice > maxPrice}). Lets the global exception handler map all of
 * them to {@code 422 Unprocessable Entity} in one place instead of one handler
 * method per aggregate.
 *
 * <p>See {@link NotFoundException} for why this also carries a {@code messageKey}/{@code args}
 * pair alongside the plain {@code message}.
 */
public abstract class InvalidQueryException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    protected InvalidQueryException(String message, String messageKey, Object... args) {
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
