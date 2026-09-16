package com.ej1.app.shared.domain.exception;

/**
 * Base type for "the query is syntactically valid but semantically nonsensical"
 * domain exceptions (e.g. {@code InvalidProductQueryException} for
 * {@code minPrice > maxPrice}). Lets the global exception handler map all of
 * them to {@code 422 Unprocessable Entity} in one place instead of one handler
 * method per aggregate.
 */
public abstract class InvalidQueryException extends RuntimeException {
    protected InvalidQueryException(String message) {
        super(message);
    }
}
