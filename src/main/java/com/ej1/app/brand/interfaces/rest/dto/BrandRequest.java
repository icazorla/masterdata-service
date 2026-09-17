package com.ej1.app.brand.interfaces.rest.dto;

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
@ValidBrandTranslations
@Schema(description = "Payload de creación/actualización de una marca")
public class BrandRequest {

    @NotEmpty
    @Schema(description = "Traducciones de la marca; clave = locale (\"en\", \"es\"...), debe incluir al menos el idioma por defecto (en)",
            example = "{\"en\":{\"name\":\"Logitech\",\"description\":\"Peripheral manufacturer\"},"
                    + "\"es\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}")
    private Map<String, @Valid BrandTranslationRequest> translations;
}
