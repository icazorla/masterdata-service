package io.masterdata.common.catalog.interfaces.rest.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida {@link CatalogRequest#getTranslations()}: cada clave debe ser uno de los
 * idiomas soportados por la aplicación, y el idioma por defecto debe estar
 * presente — {@code Catalog.resolveTranslation} depende de que siempre esté ahí
 * como fallback. No hace falta comprobar "idioma duplicado": {@code translations}
 * es un {@code Map<locale, ...>}, así que una clave repetida es estructuralmente
 * imposible. Ver {@link CatalogTranslationsValidator} para el conjunto real
 * de idiomas (de {@code application.yml}) — compartido por todos los tipos de catálogo, ya
 * que ninguno declara sus propios idiomas soportados.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CatalogTranslationsValidator.class)
public @interface ValidCatalogTranslations {

    String message() default "translations must cover the default locale exactly once each, using only supported locales";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
