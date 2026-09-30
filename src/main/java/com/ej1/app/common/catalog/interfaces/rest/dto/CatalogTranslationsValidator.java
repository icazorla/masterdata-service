package com.ej1.app.common.catalog.interfaces.rest.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;

/**
 * Deliberadamente libre de dependencias (sin {@code @Value}/búsqueda de configuración,
 * constructor simple sin argumentos) para que se comporte igual en todas partes,
 * incluidos los slices {@code @WebMvcTest}. Debe mantenerse sincronizado a mano con
 * {@code app.i18n.supported-locales}/{@code spring.web.locale} en
 * {@code application.yml} si alguno de los dos cambia.
 */
public class CatalogTranslationsValidator implements ConstraintValidator<ValidCatalogTranslations, CatalogRequest> {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "es");
    private static final String DEFAULT_LOCALE = "en";

    @Override
    public boolean isValid(CatalogRequest request, ConstraintValidatorContext context) {
        Map<String, CatalogTranslationRequest> translations = request.getTranslations();
        if (translations == null || translations.isEmpty()) {
            return true; // ya lo reporta @NotEmpty en su lugar
        }
        return SUPPORTED_LOCALES.containsAll(translations.keySet())
                && translations.containsKey(DEFAULT_LOCALE);
    }
}
