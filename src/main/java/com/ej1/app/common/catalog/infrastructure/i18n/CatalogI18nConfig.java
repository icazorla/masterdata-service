package com.ej1.app.common.catalog.infrastructure.i18n;

import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;

/**
 * Engancha {@link CatalogTypeNamesMessageSource} como padre del {@code messageSource}
 * de Spring Boot (el de {@code messages_*.properties}): las claves que no estén en los
 * bundles — en concreto {@code catalog.type.<code>} — se buscan en los
 * {@code names} de {@code app.catalog.types}. Si el {@code messageSource} ya tenía un
 * padre (p. ej. el de un contexto padre), se conserva detrás del nuestro.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogI18nConfig {

    public CatalogI18nConfig(MessageSource messageSource, CatalogProperties properties) {
        if (messageSource instanceof HierarchicalMessageSource hierarchical) {
            CatalogTypeNamesMessageSource names = new CatalogTypeNamesMessageSource(properties);
            names.setParentMessageSource(hierarchical.getParentMessageSource());
            hierarchical.setParentMessageSource(names);
        }
    }
}
