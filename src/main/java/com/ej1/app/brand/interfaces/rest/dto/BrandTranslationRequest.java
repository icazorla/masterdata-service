package com.ej1.app.brand.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A brand's {@code name}/{@code description} for one locale. The locale itself
 * is not a field here — it's the key under which this value sits in
 * {@link BrandRequest#getTranslations()}, so RFC 6901/6902 pointers can address
 * a given language directly (e.g. {@code /translations/es/description}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Nombre/descripción de una marca para un idioma concreto")
public class BrandTranslationRequest {

    @NotBlank
    @Size(max = 80)
    @Schema(description = "Nombre de la marca en este idioma", example = "Logitech", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500)
    @Schema(description = "Descripción de la marca en este idioma", example = "Fabricante de periféricos")
    private @Nullable String description;
}
