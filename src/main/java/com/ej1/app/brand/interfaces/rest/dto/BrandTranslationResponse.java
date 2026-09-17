package com.ej1.app.brand.interfaces.rest.dto;

import org.jspecify.annotations.Nullable;

/** See {@link BrandTranslationRequest} — the locale is the map key, not a field here either. */
public record BrandTranslationResponse(
        String name,
        @Nullable String description
) {
}
