package com.ej1.app.product.domain.exception;

import com.ej1.app.shared.domain.exception.InvalidQueryException;

/**
 * The query is syntactically valid (well-formed content matching the declared
 * media type, every field individually well-formed) but semantically
 * nonsensical as a product query — e.g. {@code minPrice > maxPrice}.
 *
 * <p>Maps to {@code 422 Unprocessable Entity}, matching the distinction RFC 10008
 * draws for the HTTP QUERY method: 400 for content that doesn't match the
 * declared media type (handled by Bean Validation on individual fields), 422
 * for content the server understood but rejects on business grounds.
 */
public class InvalidProductQueryException extends InvalidQueryException {
    public InvalidProductQueryException(String message) {
        super(message);
    }
}
