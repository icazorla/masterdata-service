package com.ej1.app.common.catalog.infrastructure.cache.caffeine;

import com.ej1.app.common.catalog.infrastructure.cache.spi.CacheSpec;
import com.ej1.app.common.catalog.infrastructure.cache.spi.CatalogCacheProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.stream.Collectors;

/**
 * {@link CatalogCacheProvider} para Caffeine ({@code app.catalog.cache.provider: caffeine}).
 *
 * <p>{@code nativeOptions} se interpretan como claves de {@code CaffeineSpec} ({@code initialCapacity: 64},
 * {@code weakValues: ""}…) y se aplican primero; los campos neutros de {@link CacheSpec} van después.
 * Si una opción nativa repite uno neutro (p. ej. {@code maximumSize} en los dos), Caffeine lanza
 * {@link IllegalStateException} y la app no arranca — a propósito, para no tener dos fuentes de verdad.
 * Una clave desconocida también falla ({@link IllegalArgumentException} de {@code CaffeineSpec}).
 */
public class CaffeineCatalogCacheProvider implements CatalogCacheProvider {

    public static final String ID = "caffeine";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Cache create(String name, CacheSpec spec) {
        Caffeine<Object, Object> builder = spec.nativeOptions().isEmpty()
                ? Caffeine.newBuilder()
                : Caffeine.from(toCaffeineSpec(spec));
        if (spec.maximumSize() != null) {
            builder.maximumSize(spec.maximumSize());
        }
        if (spec.expireAfterWrite() != null) {
            builder.expireAfterWrite(spec.expireAfterWrite());
        }
        if (spec.expireAfterAccess() != null) {
            builder.expireAfterAccess(spec.expireAfterAccess());
        }
        if (spec.stats()) {
            builder.recordStats();
        }
        return new CaffeineCache(name, builder.build(), false);
    }

    /** {@code {initialCapacity=64, weakValues=}} → {@code "initialCapacity=64,weakValues"}. */
    private static String toCaffeineSpec(CacheSpec spec) {
        return spec.nativeOptions().entrySet().stream()
                .map(e -> e.getValue() == null || e.getValue().isBlank() ? e.getKey() : e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }
}
