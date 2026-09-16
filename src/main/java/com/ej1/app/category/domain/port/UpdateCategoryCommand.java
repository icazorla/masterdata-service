package com.ej1.app.category.domain.port;

/**
 * Command object representing the intent to update an existing category.
 * Kept separate from {@link CreateCategoryCommand} so the use case signature
 * is explicit and the contract for updates can evolve independently.
 */
public record UpdateCategoryCommand(
        String name,
        String description
) {
}
