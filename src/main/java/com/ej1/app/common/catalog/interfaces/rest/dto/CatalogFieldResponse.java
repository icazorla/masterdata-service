package com.ej1.app.common.catalog.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Definición de un campo de las entradas de un tipo de catálogo")
public record CatalogFieldResponse(
        @Schema(description = "Nombre del atributo en el JSON de la API", example = "website")
        String name,
        @Schema(description = "Tipo de dato: string, integer, number, boolean, date o date-time", example = "string")
        String dataType,
        @Schema(description = "Obligatorio al crear/actualizar")
        boolean required,
        @Schema(description = "Lo asigna el servidor: solo aparece en la respuesta, nunca en el payload")
        boolean readOnly,
        @Schema(description = "Depende del idioma: en el payload viaja dentro de translations.<locale>")
        boolean translatable,
        @Schema(description = "Valor válido de sortBy en el listado del catálogo")
        boolean sortable,
        @Schema(description = "Campo adicional propio de este tipo (app.catalog.types.<type>.extra-fields), no común a todo catálogo")
        boolean extra
) {
}
