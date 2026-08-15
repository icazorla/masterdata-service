package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;

import java.util.List;

/**
 * A page of {@link Product} results, returned by {@link ProductRepository#search}.
 * Deliberately framework-agnostic — the domain must not depend on
 * {@code org.springframework.data.domain.Page}. Infrastructure adapters map
 * their own paging types onto this record.
 */
public record ProductPage(
        List<Product> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
