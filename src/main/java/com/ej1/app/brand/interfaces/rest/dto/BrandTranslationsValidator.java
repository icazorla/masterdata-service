package com.ej1.app.brand.interfaces.rest.dto;

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
public class BrandTranslationsValidator implements ConstraintValidator<ValidBrandTranslations, BrandRequest> {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "es");
    private static final String DEFAULT_LOCALE = "en";

    @Override
    public boolean isValid(BrandRequest request, ConstraintValidatorContext context) {
        Map<String, BrandTranslationRequest> translations = request.getTranslations();
        if (translations == null || translations.isEmpty()) {
            return true; // reported by @NotEmpty instead
        }
        return SUPPORTED_LOCALES.containsAll(translations.keySet())
                && translations.containsKey(DEFAULT_LOCALE);
    }
}
