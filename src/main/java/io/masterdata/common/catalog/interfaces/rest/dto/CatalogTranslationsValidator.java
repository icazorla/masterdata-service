package io.masterdata.common.catalog.interfaces.rest.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los idiomas salen de la misma configuración que usa el resto de la aplicación —
 * {@code app.i18n.supported-locales} y {@code spring.web.locale} —, de modo que cambiarlos en
 * {@code application.yml} cambia también esta validación. Lo instancia el
 * {@code ValidatorFactory} de Spring, que resuelve los {@code @Value} del constructor.
 */
public class CatalogTranslationsValidator implements ConstraintValidator<ValidCatalogTranslations, CatalogRequest> {

    private final Set<String> supportedLocales;
    private final String defaultLocale;

    public CatalogTranslationsValidator(@Value("${app.i18n.supported-locales:en}") String supportedLocales,
                                        @Value("${spring.web.locale:en}") String defaultLocale) {
        this.supportedLocales = Arrays.stream(supportedLocales.split(","))
                .map(String::trim)
                .filter(locale -> !locale.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.defaultLocale = defaultLocale;
    }

    @Override
    public boolean isValid(CatalogRequest request, ConstraintValidatorContext context) {
        Map<String, CatalogTranslationRequest> translations = request.getTranslations();
        if (translations == null || translations.isEmpty()) {
            return true; // ya lo reporta @NotEmpty en su lugar
        }
        return supportedLocales.containsAll(translations.keySet())
                && translations.containsKey(defaultLocale);
    }
}
