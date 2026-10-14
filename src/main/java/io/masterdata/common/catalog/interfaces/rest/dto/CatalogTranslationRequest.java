package io.masterdata.common.catalog.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * El {@code name}/{@code description} de una entrada de catálogo para un idioma
 * concreto. El propio idioma no es un campo aquí — es la clave bajo la que se
 * ubica este valor en {@link CatalogRequest#getTranslations()}, de modo que los
 * punteros RFC 6901/6902 puedan direccionar un idioma concreto directamente
 * (p. ej. {@code /translations/es/description}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Nombre/descripción de una entrada de catálogo para un idioma concreto")
public class CatalogTranslationRequest {

    @NotBlank
    @Size(max = 80)
    @Schema(description = "Nombre en este idioma", example = "Logitech", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500)
    @Schema(description = "Descripción en este idioma", example = "Fabricante de periféricos")
    private @Nullable String description;
}
