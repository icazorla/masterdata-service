package com.ej1.app.brand.interfaces.rest.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates {@link BrandRequest#getTranslations()}: every key must be one of
 * the app's supported locales, and the default locale must be present —
 * {@code Brand.resolveTranslation} relies on it always being there as the
 * fallback. No "duplicate locale" check needed anymore: {@code translations}
 * is a {@code Map<locale, ...>}, so a repeated key is structurally impossible.
 * See {@link BrandTranslationsValidator} for the actual (hardcoded) locale set.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BrandTranslationsValidator.class)
public @interface ValidBrandTranslations {

    String message() default "translations must cover the default locale exactly once each, using only supported locales";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
