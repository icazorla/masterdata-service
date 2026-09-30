package com.ej1.app.product.domain.port;

import java.math.BigDecimal;

/**
 * Objeto de comando que representa la intención de crear un nuevo producto.
 * Forma parte del puerto de entrada del dominio — independiente de cualquier DTO de transporte.
 */
public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String sku,
        Long categoryId,
        Long brandId,
        String imageUrl,
        BigDecimal weight,
        String dimensions,
        String tags,
        Boolean active
) {
}
