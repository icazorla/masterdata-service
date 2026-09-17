package com.ej1.app.category.domain.port;

import java.util.List;

/**
 * Command object representing the intent to create a new category.
 * Part of the domain's incoming port — independent of any transport DTO.
 */
public record CreateCategoryCommand(
        List<CategoryTranslationCommand> translations
) {
}
