package com.ej1.app.brand.domain.port;

/**
 * Command object representing the intent to set a brand's {@code name}/
 * {@code description} for one locale. Part of {@link CreateBrandCommand}/
 * {@link UpdateBrandCommand}, independent of any transport DTO.
 */
public record BrandTranslationCommand(String locale, String name, String description) {
}
