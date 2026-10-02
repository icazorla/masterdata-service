package com.ej1.app.common.catalog.infrastructure.i18n;

import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTypeNamesMessageSourceTest {

    private final CatalogTypeNamesMessageSource messageSource = new CatalogTypeNamesMessageSource(properties());

    private static CatalogProperties properties() {
        CatalogProperties.TypeEntry brand = new CatalogProperties.TypeEntry();
        brand.setNames(Map.of("en", "Brand", "es", "Marca", "es-MX", "Marca MX"));
        CatalogProperties.TypeEntry category = new CatalogProperties.TypeEntry();
        CatalogProperties properties = new CatalogProperties();
        properties.setTypes(Map.of("brand", brand, "category", category));
        return properties;
    }

    @Test
    void resolvesNameForExactLanguageTag() {
        assertThat(messageSource.getMessage(TestCatalogTypes.BRAND, Locale.forLanguageTag("es-MX"))).isEqualTo("Marca MX");
    }

    @Test
    void fallsBackToLanguageWhenRegionHasNoName() {
        assertThat(messageSource.getMessage(TestCatalogTypes.BRAND, Locale.forLanguageTag("es-ES"))).isEqualTo("Marca");
    }

    @Test
    void fallsBackToCapitalizedCodeWhenLanguageHasNoName() {
        assertThat(messageSource.getMessage(TestCatalogTypes.BRAND, Locale.FRENCH)).isEqualTo("Brand");
        assertThat(messageSource.getMessage(TestCatalogTypes.CATEGORY, Locale.forLanguageTag("es"))).isEqualTo("Category");
    }

    @Test
    void usesNameAsArgumentOfGenericMessage() {
        messageSource.setCommonMessages(asProperties("error.catalog.notFound", "{0}: no existe ningún registro con id={1}"));

        assertThat(messageSource.getMessage("error.catalog.notFound", new Object[]{TestCatalogTypes.BRAND, 7L}, Locale.forLanguageTag("es")))
                .isEqualTo("Marca: no existe ningún registro con id=7");
    }

    private static Properties asProperties(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return props;
    }
}
