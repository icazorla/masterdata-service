package io.masterdata.common.catalog.infrastructure.i18n;

import io.masterdata.common.catalog.infrastructure.persistence.CatalogProperties;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code MessageSource} que sirve las claves {@code catalog.type.<code>} — el nombre
 * traducido de cada {@link io.masterdata.common.catalog.domain.CatalogType} — a partir de
 * {@code app.catalog.types.<type>.names} de {@code application.yml}, en lugar de
 * {@code messages_*.properties}. Así todo lo de un tipo de catálogo (entidades, campos
 * extra y nombres) vive en una sola entrada del YAML.
 *
 * <p>{@link CatalogI18nConfig} lo engancha como padre del {@code messageSource} de Spring
 * Boot: una clave {@code catalog.type.<code>} en {@code messages_*.properties} sigue
 * teniendo prioridad. Resolución por idioma: etiqueta exacta (p. ej. {@code es-MX}),
 * luego solo el idioma ({@code es}); si no hay ninguna, devuelve {@code null} y
 * {@code CatalogType#getDefaultMessage()} aporta el código capitalizado ({@code Brand}).
 */
public class CatalogTypeNamesMessageSource extends AbstractMessageSource {

    private static final String CODE_PREFIX = "catalog.type.";

    /** {@code catalog.type.<code>} → (etiqueta de idioma → nombre). */
    private final Map<String, Map<String, String>> namesByCode = new HashMap<>();

    public CatalogTypeNamesMessageSource(CatalogProperties properties) {
        properties.getTypes().forEach((type, entry) -> {
            Map<String, String> byLanguage = new HashMap<>();
            entry.getNames().forEach((tag, name) -> byLanguage.put(Locale.forLanguageTag(tag).toLanguageTag(), name));
            namesByCode.put(CODE_PREFIX + type.trim().toLowerCase(Locale.ROOT), byLanguage);
        });
    }

    @Override
    protected String resolveCodeWithoutArguments(String code, Locale locale) {
        Map<String, String> byLanguage = namesByCode.get(code);
        if (byLanguage == null) {
            return null;
        }
        String name = byLanguage.get(locale.toLanguageTag());
        return name != null ? name : byLanguage.get(locale.getLanguage());
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        String name = resolveCodeWithoutArguments(code, locale);
        return name != null ? createMessageFormat(name, locale) : null;
    }
}
