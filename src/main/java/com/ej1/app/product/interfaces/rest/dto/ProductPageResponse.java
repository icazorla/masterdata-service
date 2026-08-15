package com.ej1.app.product.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página de resultados de una consulta de productos")
public record ProductPageResponse(
        List<ProductResponse> content,
        @Schema(description = "Página actual (0-based)") int page,
        @Schema(description = "Tamaño de página solicitado") int size,
        @Schema(description = "Total de elementos que cumplen los criterios") long totalElements,
        @Schema(description = "Total de páginas") int totalPages
) {
}
