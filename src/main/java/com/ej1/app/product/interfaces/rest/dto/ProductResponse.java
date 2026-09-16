package com.ej1.app.product.interfaces.rest.dto;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        @Nullable String description,
        BigDecimal price,
        Integer stock,
        String sku,
        @Nullable CategoryRef category,
        @Nullable BrandRef brand,
        @Nullable String imageUrl,
        @Nullable BigDecimal weight,
        @Nullable String dimensions,
        @Nullable String tags,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
