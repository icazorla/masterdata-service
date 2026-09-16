package com.ej1.app.product.interfaces.rest.dto;

/**
 * Lean {@code {id, name}} projection of a brand, embedded in {@link ProductResponse}.
 * Deliberately not {@code com.ej1.app.brand.interfaces.rest.dto.BrandResponse}
 * (which also carries {@code description}/timestamps) — a product listing only needs
 * enough to identify and label the brand, not the full master-data record.
 */
public record BrandRef(Long id, String name) {
}
