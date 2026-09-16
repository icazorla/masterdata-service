package com.ej1.app.shared.domain;

import java.util.Set;
import java.util.function.Function;

/**
 * Validates that a {@code sortBy} value names an actual sortable field, throwing the
 * caller's own exception when it doesn't. Pure Java, no framework dependency, so any
 * feature's domain service can use it without breaking the "domain knows nothing about
 * frameworks" rule.
 *
 * <p>Deliberately does <em>not</em> own the set of allowed fields or the exception type:
 * which fields are sortable is knowledge specific to each aggregate (e.g. {@code Product}),
 * and which exception maps to which HTTP status is a decision each feature's own domain
 * exception already encodes (see e.g. {@code InvalidProductQueryException}). This class only
 * shares the repetitive mechanics — check membership, format a consistent message, throw —
 * so a second entity's service reuses this instead of copy-pasting the same {@code if}.
 */
public final class SortableFieldValidator {

    private SortableFieldValidator() {
    }

    /**
     * Throws {@code exceptionFactory.apply(message)} when {@code sortBy} is not in
     * {@code allowedFields}. Does nothing otherwise.
     */
    public static void requireSortable(String sortBy, Set<String> allowedFields,
                                        Function<String, ? extends RuntimeException> exceptionFactory) {
        if (!allowedFields.contains(sortBy)) {
            throw exceptionFactory.apply(
                    "sortBy '%s' is not sortable. Allowed values: %s".formatted(sortBy, allowedFields));
        }
    }
}
