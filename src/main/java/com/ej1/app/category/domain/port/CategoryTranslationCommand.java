package com.ej1.app.category.domain.port;

/**
 * Command object representing the intent to set a category's {@code name}/
 * {@code description} for one locale. Part of {@link CreateCategoryCommand}/
 * {@link UpdateCategoryCommand}, independent of any transport DTO.
 */
public record CategoryTranslationCommand(String locale, String name, String description) {
}
