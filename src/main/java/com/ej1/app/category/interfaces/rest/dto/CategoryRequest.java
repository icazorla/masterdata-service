package com.ej1.app.category.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload de creación/actualización de una categoría")
public class CategoryRequest {

    @NotBlank
    @Size(max = 80)
    @Schema(description = "Nombre de la categoría", example = "Periféricos", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500)
    @Schema(description = "Descripción de la categoría", example = "Teclados, ratones y accesorios")
    private @Nullable String description;
}
