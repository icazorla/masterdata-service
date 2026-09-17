package com.ej1.app.category.domain;

import org.jspecify.annotations.Nullable;

/**
 * A {@link Category}'s {@code name}/{@code description} for one locale. A category
 * carries one of these per supported language instead of a single hardcoded
 * pair of strings.
 */
public record CategoryTranslation(String locale, String name, @Nullable String description) {
}
