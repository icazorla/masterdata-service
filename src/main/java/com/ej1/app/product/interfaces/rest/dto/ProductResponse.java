package com.ej1.app.product.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
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
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
