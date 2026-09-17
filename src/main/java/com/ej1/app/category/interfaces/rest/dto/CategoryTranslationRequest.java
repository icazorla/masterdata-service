package com.ej1.app.category.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A category's {@code name}/{@code description} for one locale. The locale itself
 * is not a field here — it's the key under which this value sits in
 * {@link CategoryRequest#getTranslations()}, so RFC 6901/6902 pointers can address
 * a given language directly (e.g. {@code /translations/es/description}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Nombre/descripción de una categoría para un idioma concreto")
public class CategoryTranslationRequest {

    @NotBlank
    @Size(max = 80)
    @Schema(description = "Nombre de la categoría en este idioma", example = "Periféricos", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500)
    @Schema(description = "Descripción de la categoría en este idioma", example = "Teclados, ratones y accesorios")
    private @Nullable String description;
}
