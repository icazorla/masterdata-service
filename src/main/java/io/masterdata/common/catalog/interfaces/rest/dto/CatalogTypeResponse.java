package io.masterdata.common.catalog.interfaces.rest.dto;

import io.masterdata.common.catalog.domain.CatalogType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Un tipo de catálogo configurado y la definición de sus campos")
public record CatalogTypeResponse(
        @Schema(description = "Código del tipo, el segmento {type} de /api/catalogs/{type}", example = "province", implementation = String.class)
        CatalogType type,
        @Schema(description = "Nombre del tipo traducido según Accept-Language", example = "Marca")
        String name,
        @Schema(description = "Campos comunes a todo catálogo seguidos de los adicionales de este tipo")
        List<CatalogFieldResponse> fields
) {
}
