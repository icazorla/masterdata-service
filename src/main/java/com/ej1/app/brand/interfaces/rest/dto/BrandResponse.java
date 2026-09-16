package com.ej1.app.brand.interfaces.rest.dto;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String name,
        @Nullable String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
