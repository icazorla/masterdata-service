package com.ej1.app.category.interfaces.rest.dto;

import org.jspecify.annotations.Nullable;

/** See {@link CategoryTranslationRequest} — the locale is the map key, not a field here either. */
public record CategoryTranslationResponse(
        String name,
        @Nullable String description
) {
}
