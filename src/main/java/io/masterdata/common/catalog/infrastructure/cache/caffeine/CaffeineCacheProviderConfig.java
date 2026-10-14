package io.masterdata.common.catalog.infrastructure.cache.caffeine;

import io.masterdata.common.catalog.infrastructure.cache.spi.CatalogCacheProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activa {@link CaffeineCatalogCacheProvider} si Caffeine está en el classpath y
 * {@code app.catalog.cache.provider} es {@code caffeine} (o no se indica: es el proveedor por defecto).
 * La importa {@code CatalogCacheConfig}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
@ConditionalOnProperty(prefix = "app.catalog.cache", name = "provider",
        havingValue = CaffeineCatalogCacheProvider.ID, matchIfMissing = true)
public class CaffeineCacheProviderConfig {

    @Bean
    public CatalogCacheProvider caffeineCatalogCacheProvider() {
        return new CaffeineCatalogCacheProvider();
    }
}
