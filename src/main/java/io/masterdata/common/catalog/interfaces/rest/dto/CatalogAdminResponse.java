package io.masterdata.common.catalog.interfaces.rest.dto;

import io.masterdata.common.catalog.domain.CatalogType;
import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Respuesta de la API de gestión ({@code /admin/api/catalogs/{type}}): a diferencia de
 * {@link CatalogResponse} (API de consulta, un único {@code name}/{@code description}
 * resuelto según {@code Accept-Language}), lleva <b>todas</b> las traducciones indexadas
 * por locale — la misma forma que {@link CatalogRequest#getTranslations()}, así que un
 * formulario de edición puede reenviar lo que recibe. {@code extraFields} viaja aplanado
 * como atributos de primer nivel, igual que en {@link CatalogResponse}. {@code version} es la que
 * se reenvía en {@code If-Match} al modificar la entrada.
 */
public record CatalogAdminResponse(
        Long id,
        CatalogType type,
        int weight,
        boolean enabled,
        Map<String, CatalogTranslationResponse> translations,
        Map<String, Object> extraFields,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    @JsonAnyGetter
    @Override
    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
