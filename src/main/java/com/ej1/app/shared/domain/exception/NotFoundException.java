package com.ej1.app.shared.domain.exception;

/**
 * Base type for "the requested resource does not exist" domain exceptions
 * (e.g. {@code ProductNotFoundException}, {@code CategoryNotFoundException},
 * {@code BrandNotFoundException}). Lets the global exception handler map all
 * of them to {@code 404 Not Found} in one place instead of one handler method
 * per aggregate.
 */
public abstract class NotFoundException extends RuntimeException {
    protected NotFoundException(String message) {
        super(message);
    }
}
