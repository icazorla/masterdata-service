package com.ej1.app.category.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ValidCategoryTranslations
@Schema(description = "Payload de creación/actualización de una categoría")
public class CategoryRequest {

    @NotEmpty
    @Schema(description = "Traducciones de la categoría; clave = locale (\"en\", \"es\"...), debe incluir al menos el idioma por defecto (en)",
            example = "{\"en\":{\"name\":\"Peripherals\",\"description\":\"Keyboards, mice and accessories\"},"
                    + "\"es\":{\"name\":\"Periféricos\",\"description\":\"Teclados, ratones y accesorios\"}}")
    private Map<String, @Valid CategoryTranslationRequest> translations;
}
