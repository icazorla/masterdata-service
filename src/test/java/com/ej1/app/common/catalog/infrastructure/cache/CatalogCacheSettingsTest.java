package com.ej1.app.common.catalog.infrastructure.cache;

import com.ej1.app.common.catalog.infrastructure.cache.spi.CacheSpec;
import com.ej1.app.common.catalog.infrastructure.cache.spi.CatalogCacheProvider;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties.CacheSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuración neutra de la caché: valores de serie, herencia campo a campo de
 * {@code app.catalog.cache} a cada tipo, conversión a {@link CacheSpec} y elección del
 * {@link CatalogCacheProvider} por {@code app.catalog.cache.provider}.
 */
class CatalogCacheSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogCacheConfig.class);

    @Test
    @DisplayName("Valores de serie: Caffeine, activada, 1000 entradas, 10 min por escritura, estadísticas")
    void builtInDefaults() {
        var defaults = new CatalogProperties().getCache();

        assertThat(defaults.getProvider()).isEqualTo("caffeine");
        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.getMaximumSize()).isEqualTo(1000);
        assertThat(defaults.getExpireAfterWrite()).isEqualTo(Duration.ofMinutes(10));
        assertThat(defaults.getExpireAfterAccess()).isNull();
        assertThat(defaults.isStats()).isTrue();
    }

    @Test
    @DisplayName("Un tipo hereda campo a campo: solo cambia lo que indica")
    void typeInheritsFieldByField() {
        var defaults = new CatalogProperties().getCache();
        var type = new CacheSettings();
        type.setExpireAfterWrite(Duration.ofHours(1));

        CacheSettings effective = type.effectiveFor(defaults);

        assertThat(effective.getExpireAfterWrite()).isEqualTo(Duration.ofHours(1));
        assertThat(effective.getMaximumSize()).isEqualTo(1000);
        assertThat(effective.isEnabled()).isTrue();
        assertThat(effective.isStats()).isTrue();
    }

    @Test
    @DisplayName("nativeOptions se fusionan con las globales; en una clave repetida gana el tipo")
    void nativeOptionsAreMerged() {
        var defaults = new CatalogProperties().getCache();
        defaults.setNativeOptions(Map.of("initialCapacity", "16", "weakValues", ""));
        var type = new CacheSettings();
        type.setNativeOptions(Map.of("initialCapacity", "64"));

        assertThat(type.effectiveFor(defaults).getNativeOptions())
                .containsExactlyInAnyOrderEntriesOf(Map.of("initialCapacity", "64", "weakValues", ""));
    }

    @Test
    @DisplayName("Las propiedades llegan al proveedor como CacheSpec, por caché y con la herencia aplicada")
    void propertiesReachTheProviderAsCacheSpecs() {
        List<String> created = new ArrayList<>();
        Map<String, CacheSpec> specs = new java.util.LinkedHashMap<>();
        CatalogCacheProvider recording = new CatalogCacheProvider() {
            @Override
            public String id() {
                return "recording";
            }

            @Override
            public org.springframework.cache.Cache create(String name, CacheSpec spec) {
                created.add(name);
                specs.put(name, spec);
                return new ConcurrentMapCache(name, false);
            }
        };

        runner.withBean(CatalogCacheProvider.class, () -> recording)
                .withPropertyValues(
                        "app.catalog.cache.provider=recording",
                        "app.catalog.types.brand.cache.maximum-size=50",
                        "app.catalog.types.category.cache.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(created).containsExactlyInAnyOrder("catalogTypes", "catalogById:brand", "catalogLists:brand");
                    assertThat(specs.get("catalogById:brand"))
                            .isEqualTo(new CacheSpec(50L, Duration.ofMinutes(10), null, true, Map.of()));
                    assertThat(specs.get("catalogTypes").maximumSize()).isEqualTo(1000);
                    assertThat(ctx.getBean(CacheManager.class).getCacheNames()).hasSize(3);
                });
    }

    @Test
    @DisplayName("Por defecto se usa el proveedor de Caffeine")
    void caffeineIsTheDefaultProvider() {
        runner.withPropertyValues("app.catalog.types.brand.names.en=Brand").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CatalogCacheProvider.class).id()).isEqualTo("caffeine");
        });
    }

    @Test
    @DisplayName("Un proveedor sin adaptador disponible hace fallar el arranque con un mensaje claro")
    void unknownProviderFailsAtStartup() {
        runner.withPropertyValues("app.catalog.cache.provider=cache2k").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .hasMessageContaining("No catalog cache provider 'cache2k'");
        });
    }
}
