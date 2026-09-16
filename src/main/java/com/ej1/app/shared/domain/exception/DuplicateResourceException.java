package com.ej1.app.shared.domain.exception;

/**
 * Base type for "a resource with this unique key already exists" domain
 * exceptions (e.g. {@code DuplicateSkuException}, {@code DuplicateCategoryNameException},
 * {@code DuplicateBrandNameException}). Lets the global exception handler map all
 * of them to {@code 409 Conflict} in one place instead of one handler method
 * per aggregate.
 */
public abstract class DuplicateResourceException extends RuntimeException {
    protected DuplicateResourceException(String message) {
        super(message);
    }
}
