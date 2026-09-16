package com.ej1.app.brand.interfaces.rest.dto;

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
@Schema(description = "Payload de creación/actualización de una marca")
public class BrandRequest {

    @NotBlank
    @Size(max = 80)
    @Schema(description = "Nombre de la marca", example = "Logitech", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500)
    @Schema(description = "Descripción de la marca", example = "Fabricante de periféricos")
    private @Nullable String description;
}
