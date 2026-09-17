package com.ej1.app.category.domain.port;

import java.util.List;

/**
 * Command object representing the intent to update an existing category.
 * Kept separate from {@link CreateCategoryCommand} so the use case signature
 * is explicit and the contract for updates can evolve independently.
 */
public record UpdateCategoryCommand(
        List<CategoryTranslationCommand> translations
) {
}
