package com.ej1.app.product.interfaces.rest.dto;

/**
 * Lean {@code {id, name}} projection of a category, embedded in {@link ProductResponse}.
 * Deliberately not {@code com.ej1.app.category.interfaces.rest.dto.CategoryResponse}
 * (which also carries {@code description}/timestamps) — a product listing only needs
 * enough to identify and label the category, not the full master-data record.
 */
public record CategoryRef(Long id, String name) {
}
