package com.ej1.app.common.catalog.interfaces.rest.dto;

import org.jspecify.annotations.Nullable;

/** Ver {@link CatalogTranslationRequest} — el idioma es la clave del mapa, tampoco es un campo aquí. */
public record CatalogTranslationResponse(
        String name,
        @Nullable String description
) {
}
