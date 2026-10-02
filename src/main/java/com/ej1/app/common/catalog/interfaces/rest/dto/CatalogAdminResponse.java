package com.ej1.app.common.catalog.interfaces.rest.dto;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Respuesta de la API de gestión ({@code /admin/api/catalogs/{type}}): a diferencia de
 * {@link CatalogResponse} (API de consulta, un único {@code name}/{@code description}
 * resuelto según {@code Accept-Language}), lleva <b>todas</b> las traducciones indexadas
 * por locale — la misma forma que {@link CatalogRequest#getTranslations()}, así que un
 * formulario de edición puede reenviar lo que recibe. {@code extraFields} viaja aplanado
 * como atributos de primer nivel, igual que en {@link CatalogResponse}.
 */
public record CatalogAdminResponse(
        Long id,
        CatalogType type,
        int weight,
        Map<String, CatalogTranslationResponse> translations,
        Map<String, Object> extraFields,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    @JsonAnyGetter
    @Override
    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
