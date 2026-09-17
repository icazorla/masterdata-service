package com.ej1.app.brand.domain;

import org.jspecify.annotations.Nullable;

/**
 * A {@link Brand}'s {@code name}/{@code description} for one locale. A brand
 * carries one of these per supported language instead of a single hardcoded
 * pair of strings.
 */
public record BrandTranslation(String locale, String name, @Nullable String description) {
}
