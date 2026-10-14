package io.masterdata.common.catalog.infrastructure.cache;

import io.masterdata.common.catalog.infrastructure.cache.CatalogCachesEndpoint.CacheReport;
import io.masterdata.common.catalog.infrastructure.cache.CatalogCachesEndpoint.CatalogCachesReport;
import io.masterdata.common.catalog.infrastructure.cache.CatalogCachesEndpoint.SettingsReport;
import io.masterdata.common.catalog.infrastructure.cache.CatalogCachesEndpoint.TypeReport;
import io.masterdata.common.catalog.infrastructure.persistence.CatalogProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CatalogCachesEndpoint}: configuración efectiva por tipo (de las propiedades) y tamaño/estadísticas
 * (de Micrometer). Aquí el registro de métricas se monta a mano con el binder de Caffeine — en la app lo
 * hace Spring Boot para la librería que toque —; el endpoint en sí no sabe qué librería hay debajo.
 */
@SpringJUnitConfig({CatalogCacheConfig.class, CatalogCachesEndpointTest.MetricsConfig.class})
@TestPropertySource(properties = {
        "app.catalog.cache.maximum-size=100",
        "app.catalog.cache.stats=false",
        "app.catalog.types.region.cache.maximum-size=50",
        "app.catalog.types.region.cache.expire-after-write=1h",
        "app.catalog.types.region.cache.stats=true",
        "app.catalog.types.region.cache.native-options.initialCapacity=8",
        "app.catalog.types.province.cache.enabled=false"
})
class CatalogCachesEndpointTest {

    @Autowired
    private CatalogCachesEndpoint endpoint;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CatalogProperties properties;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("Muestra el proveedor, la configuración global y la efectiva de cada tipo (heredada campo a campo)")
    void reportsProviderDefaultsAndEffectiveSettingsPerType() {
        CatalogCachesReport report = endpoint.caches();

        assertThat(report.provider()).isEqualTo("caffeine");
        assertThat(report.defaults().maximumSize()).isEqualTo(100);
        assertThat(report.defaults().expireAfterWrite()).isEqualTo("PT10M");
        assertThat(report.types()).containsOnlyKeys("region", "province");
        SettingsReport region = report.types().get("region").settings();
        assertThat(region.maximumSize()).isEqualTo(50);
        assertThat(region.expireAfterWrite()).isEqualTo("PT1H");
        assertThat(region.stats()).isTrue();
        assertThat(region.nativeOptions()).isEqualTo(Map.of("initialCapacity", "8"));
        SettingsReport province = report.types().get("province").settings();
        assertThat(province.enabled()).isFalse();
        assertThat(province.maximumSize()).isEqualTo(100);  // heredado
        assertThat(report.types().get("region").caches()).containsOnlyKeys("catalogById:region", "catalogLists:region");
    }

    @Test
    @DisplayName("Una caché activa muestra tamaño y estadísticas de Micrometer")
    void activeCacheReportsSizeAndStats() {
        Cache byId = cacheManager.getCache("catalogById:region");
        byId.put(1L, "a");
        byId.put(2L, "b");
        byId.get(1L);   // hit
        byId.get(3L);   // miss

        CacheReport report = endpoint.caches().types().get("region").caches().get("catalogById:region");

        assertThat(report.active()).isTrue();
        assertThat(report.size()).isEqualTo(2);
        assertThat(report.stats()).isNotNull();
        assertThat(report.stats().requests()).isEqualTo(2);
        assertThat(report.stats().hits()).isEqualTo(1);
        assertThat(report.stats().misses()).isEqualTo(1);
        assertThat(report.stats().hitRate()).isEqualTo(0.5);
        assertThat(report.stats().evictions()).isZero();
    }

    @Test
    @DisplayName("Sin peticiones, hitRate es null (no un 100% engañoso)")
    void hitRateIsNullWithoutRequests() {
        CacheReport report = endpoint.caches().types().get("region").caches().get("catalogLists:region");

        assertThat(report.stats().requests()).isZero();
        assertThat(report.stats().hitRate()).isNull();
    }

    @Test
    @DisplayName("Con stats=false, stats es null (no ceros engañosos)")
    void cacheWithoutStatsHasNoStats() {
        CacheReport types = endpoint.caches().catalogTypes();

        assertThat(types.active()).isTrue();
        assertThat(types.stats()).isNull();
    }

    @Test
    @DisplayName("Las cachés de un tipo desactivado aparecen como inactivas, sin datos")
    void disabledTypeCachesAreInactive() {
        CacheReport report = endpoint.caches().types().get("province").caches().get("catalogLists:province");

        assertThat(report.active()).isFalse();
        assertThat(report.size()).isNull();
        assertThat(report.stats()).isNull();
    }

    @Test
    @DisplayName("Sin MeterRegistry, tamaño y estadísticas van a null; la configuración se sigue mostrando")
    void withoutMeterRegistrySizeAndStatsAreNull() {
        var withoutMetrics = new CatalogCachesEndpoint(properties,
                new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));

        CacheReport report = withoutMetrics.caches().types().get("region").caches().get("catalogById:region");

        assertThat(report.active()).isTrue();
        assertThat(report.size()).isNull();
        assertThat(report.stats()).isNull();
        assertThat(withoutMetrics.caches().types().get("region").settings().maximumSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("/{type} devuelve solo ese tipo, y null (→ 404) si no está configurado")
    void selectorReturnsOneTypeOrNull() {
        TypeReport region = endpoint.type("region");

        assertThat(region).isNotNull();
        assertThat(region.caches()).containsOnlyKeys("catalogById:region", "catalogLists:region");
        assertThat(endpoint.type("widget")).isNull();
    }

    @Configuration
    static class MetricsConfig {

        /** Lo que Spring Boot hace en la app (CacheMetricsRegistrar): un binder por caché, con la etiqueta cache=<nombre>. */
        @Bean
        @SuppressWarnings("unchecked")
        MeterRegistry meterRegistry(CacheManager cacheManager) {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            cacheManager.getCacheNames().forEach(name -> CaffeineCacheMetrics.monitor(registry,
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cacheManager.getCache(name).getNativeCache(), name));
            return registry;
        }
    }
}
