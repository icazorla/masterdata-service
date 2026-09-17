package com.ej1.app.category.interfaces.rest.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;

/**
 * Deliberately dependency-free (no {@code @Value}/config lookup, plain no-arg
 * constructor) so it behaves identically everywhere, including
 * {@code @WebMvcTest} slices. Must be kept in sync by hand with
 * {@code app.i18n.supported-locales}/{@code spring.web.locale} in
 * {@code application.yml} if either changes.
 */
public class CategoryTranslationsValidator implements ConstraintValidator<ValidCategoryTranslations, CategoryRequest> {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "es");
    private static final String DEFAULT_LOCALE = "en";

    @Override
    public boolean isValid(CategoryRequest request, ConstraintValidatorContext context) {
        Map<String, CategoryTranslationRequest> translations = request.getTranslations();
        if (translations == null || translations.isEmpty()) {
            return true; // reported by @NotEmpty instead
        }
        return SUPPORTED_LOCALES.containsAll(translations.keySet())
                && translations.containsKey(DEFAULT_LOCALE);
    }
}
