package com.ej1.app.product.domain.exception;

import com.ej1.app.shared.domain.exception.InvalidQueryException;

import java.util.Set;

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

    public InvalidProductQueryException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }

    /**
     * Matches {@link com.ej1.app.shared.domain.SortableFieldValidator}'s exception-factory
     * shape so {@code InvalidProductQueryException::new} can be used as a method reference there.
     */
    public InvalidProductQueryException(String sortBy, Set<String> allowedFields) {
        super("sortBy '" + sortBy + "' is not sortable. Allowed values: " + allowedFields,
                "error.query.invalidSortField", sortBy, allowedFields);
    }
}
