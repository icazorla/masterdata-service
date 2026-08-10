package com.ej1.app.product.domain.port;

import java.math.BigDecimal;

/**
 * Command object representing the intent to create a new product.
 * Part of the domain's incoming port — independent of any transport DTO.
 */
public record CreateProductCommand(
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
