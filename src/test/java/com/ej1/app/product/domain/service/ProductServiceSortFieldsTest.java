package com.ej1.app.product.domain.service;

import com.ej1.app.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards against {@link ProductService#ALLOWED_SORT_FIELDS} silently drifting away from
 * {@link Product}'s actual fields. {@code ALLOWED_SORT_FIELDS} is a hand-written
 * {@code Set<String>} with no compile-time link to {@code Product} — nothing stops a
 * {@code Product} field from being renamed or removed without updating the set, and a stale
 * entry wouldn't surface until a {@code sortBy} request for it failed downstream in
 * {@code ProductSpecifications}/JPA instead of with a clear 422. This test turns that into an
 * immediate, readable failure instead.
 */
class ProductServiceSortFieldsTest {

    @Test
    @DisplayName("cada campo en ALLOWED_SORT_FIELDS tiene un getter real en Product")
    void allowedSortFields_matchExistingProductGetters() {
        for (String field : ProductService.ALLOWED_SORT_FIELDS) {
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            assertThatCode(() -> Product.class.getMethod(getter))
                    .as("ALLOWED_SORT_FIELDS contiene '%s' pero Product ya no tiene %s()", field, getter)
                    .doesNotThrowAnyException();
        }
    }
}
