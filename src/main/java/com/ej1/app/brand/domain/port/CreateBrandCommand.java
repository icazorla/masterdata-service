package com.ej1.app.brand.domain.port;

import java.util.List;

/**
 * Command object representing the intent to create a new brand.
 * Part of the domain's incoming port — independent of any transport DTO.
 */
public record CreateBrandCommand(
        List<BrandTranslationCommand> translations
) {
}
