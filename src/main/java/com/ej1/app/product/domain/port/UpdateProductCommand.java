package com.ej1.app.product.domain.port;

import java.math.BigDecimal;

/**
 * Command object representing the intent to update an existing product.
 * Kept separate from {@link CreateProductCommand} so the use case signature
 * is explicit and the contract for updates can evolve independently.
 */
public record UpdateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String sku,
        String category,
        String brand,
        String imageUrl,
        BigDecimal weight,
        String dimensions,
        String tags,
        Boolean active
) {
}
