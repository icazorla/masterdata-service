package io.masterdata.common.catalog.infrastructure.i18n;

import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.infrastructure.persistence.CatalogProperties;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTypeNamesMessageSourceTest {

    private final CatalogTypeNamesMessageSource messageSource = new CatalogTypeNamesMessageSource(properties());

    private static CatalogProperties properties() {
        CatalogProperties.TypeEntry region = new CatalogProperties.TypeEntry();
        region.setNames(Map.of("en", "Autonomous community", "es", "Comunidad autónoma", "es-MX", "Comunidad autónoma MX"));
        CatalogProperties.TypeEntry province = new CatalogProperties.TypeEntry();
        CatalogProperties properties = new CatalogProperties();
        properties.setTypes(Map.of("region", region, "province", province));
        return properties;
    }

    @Test
    void resolvesNameForExactLanguageTag() {
        assertThat(messageSource.getMessage(TestCatalogTypes.REGION, Locale.forLanguageTag("es-MX"))).isEqualTo("Comunidad autónoma MX");
    }

    @Test
    void fallsBackToLanguageWhenRegionHasNoName() {
        assertThat(messageSource.getMessage(TestCatalogTypes.REGION, Locale.forLanguageTag("es-ES"))).isEqualTo("Comunidad autónoma");
    }

    @Test
    void fallsBackToCapitalizedCodeWhenLanguageHasNoName() {
        assertThat(messageSource.getMessage(TestCatalogTypes.REGION, Locale.FRENCH)).isEqualTo("Region");
        assertThat(messageSource.getMessage(TestCatalogTypes.PROVINCE, Locale.forLanguageTag("es"))).isEqualTo("Province");
    }

    @Test
    void usesNameAsArgumentOfGenericMessage() {
        messageSource.setCommonMessages(asProperties("error.catalog.notFound", "{0}: no existe ningún registro con id={1}"));

        assertThat(messageSource.getMessage("error.catalog.notFound", new Object[]{TestCatalogTypes.REGION, 7L}, Locale.forLanguageTag("es")))
                .isEqualTo("Comunidad autónoma: no existe ningún registro con id=7");
    }

    private static Properties asProperties(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return props;
    }
}
