package io.masterdata.common.catalog.infrastructure.cache.caffeine;

import io.masterdata.common.catalog.infrastructure.cache.spi.CacheSpec;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Traducción de un {@link CacheSpec} neutro a una caché de Caffeine. */
class CaffeineCatalogCacheProviderTest {

    private final CaffeineCatalogCacheProvider provider = new CaffeineCatalogCacheProvider();

    private static CacheSpec spec(Long maximumSize, Duration write, Duration access, boolean stats, Map<String, String> nativeOptions) {
        return new CacheSpec(maximumSize, write, access, stats, nativeOptions);
    }

    private Cache<Object, Object> nativeOf(org.springframework.cache.Cache cache) {
        return ((CaffeineCache) cache).getNativeCache();
    }

    @Test
    @DisplayName("Devuelve la CaffeineCache estándar de Spring (la que Boot conecta a Micrometer), sin admitir nulls")
    void returnsStandardSpringCaffeineCache() {
        var cache = provider.create("c", spec(10L, null, null, false, Map.of()));

        assertThat(cache).isInstanceOf(CaffeineCache.class);
        assertThat(cache.getName()).isEqualTo("c");
        assertThat(((CaffeineCache) cache).isAllowNullValues()).isFalse();
    }

    @Test
    void appliesSizeExpiryAndStats() {
        var policy = nativeOf(provider.create("c", spec(50L, Duration.ofHours(1), Duration.ofMinutes(5), true, Map.of()))).policy();

        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(50);
        assertThat(policy.expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofHours(1));
        assertThat(policy.expireAfterAccess().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.isRecordingStats()).isTrue();
    }

    @Test
    @DisplayName("Los campos null no configuran nada: sin límite, sin caducidad, sin estadísticas")
    void nullFieldsMeanUnbounded() {
        var policy = nativeOf(provider.create("c", spec(null, null, null, false, Map.of()))).policy();

        assertThat(policy.eviction()).isEmpty();
        assertThat(policy.expireAfterWrite()).isEmpty();
        assertThat(policy.expireAfterAccess()).isEmpty();
        assertThat(policy.isRecordingStats()).isFalse();
    }

    @Test
    @DisplayName("nativeOptions se aplican como claves de CaffeineSpec")
    void appliesNativeOptions() {
        var policy = nativeOf(provider.create("c", spec(10L, null, null, false, Map.of("expireAfterAccess", "5m")))).policy();

        assertThat(policy.expireAfterAccess().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.eviction().orElseThrow().getMaximum()).isEqualTo(10);
    }

    @Test
    @DisplayName("Una opción nativa que repite un campo neutro falla (dos fuentes de verdad)")
    void nativeOptionConflictingWithNeutralFieldFails() {
        assertThatThrownBy(() -> provider.create("c", spec(10L, null, null, false, Map.of("maximumSize", "20"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Una opción nativa desconocida falla en vez de ignorarse")
    void unknownNativeOptionFails() {
        assertThatThrownBy(() -> provider.create("c", spec(10L, null, null, false, Map.of("teleport", "yes"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
