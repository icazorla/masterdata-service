package com.ej1.app.common.catalog.infrastructure.cache;

import com.ej1.app.common.catalog.domain.service.CatalogCacheNames;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties.CacheSettings;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de Actuator {@code /actuator/catalogcaches}: estado de las cachés de catálogo agrupado
 * por tipo. {@code /actuator/catalogcaches/{type}} devuelve solo un tipo (404 si no está configurado).
 *
 * <p>Independiente de la librería de caché: la configuración sale de las propiedades (la
 * <em>efectiva</em>, ya heredada de {@code app.catalog.cache}) y el tamaño y las estadísticas de
 * Micrometer ({@code cache.size}, {@code cache.gets}, {@code cache.evictions} con la etiqueta
 * {@code cache=<nombre>}), que Spring Boot conecta para las librerías que soporta (Caffeine, cache2k,
 * JCache, Hazelcast, Redis…). Si no hay {@link MeterRegistry} o la librería no publica esas métricas,
 * esos campos van a {@code null} en vez de inventar ceros.
 *
 * <p>Complementa al endpoint estándar {@code /actuator/caches}, que solo lista nombres. Es de solo
 * lectura. Las estadísticas son acumuladas desde el arranque.
 */
@Endpoint(id = "catalogcaches")
public class CatalogCachesEndpoint {

    private final CatalogProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public CatalogCachesEndpoint(CatalogProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @ReadOperation
    public CatalogCachesReport caches() {
        Map<String, TypeReport> types = new LinkedHashMap<>();
        properties.getTypes().keySet().forEach(type -> types.put(type, typeReport(type)));
        CacheSettings defaults = properties.getCache();
        return new CatalogCachesReport(
                properties.getCache().getProvider(),
                SettingsReport.of(defaults),
                cacheReport(CatalogCacheNames.TYPES, defaults),
                types);
    }

    @ReadOperation
    public @Nullable TypeReport type(@Selector String type) {
        return properties.getTypes().containsKey(type) ? typeReport(type) : null;
    }

    private TypeReport typeReport(String type) {
        CacheSettings settings = properties.getTypes().get(type).getCache().effectiveFor(properties.getCache());
        Map<String, CacheReport> caches = new LinkedHashMap<>();
        for (String baseName : CatalogCacheNames.PER_TYPE) {
            String name = CatalogCacheNames.forType(baseName, type);
            caches.put(name, cacheReport(name, settings));
        }
        return new TypeReport(SettingsReport.of(settings), caches);
    }

    private CacheReport cacheReport(String name, CacheSettings settings) {
        if (!settings.isEnabled()) {
            return new CacheReport(false, null, null);
        }
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return new CacheReport(true, null, null);
        }
        Double size = sum(registry.find("cache.size").tag("cache", name).meters());
        StatsReport stats = settings.isStats() ? stats(registry, name) : null;
        return new CacheReport(true, size == null ? null : size.longValue(), stats);
    }

    private static @Nullable StatsReport stats(MeterRegistry registry, String name) {
        Double hits = sum(registry.find("cache.gets").tags("cache", name, "result", "hit").meters());
        Double misses = sum(registry.find("cache.gets").tags("cache", name, "result", "miss").meters());
        if (hits == null || misses == null) {
            return null;  // la librería no publica estas métricas
        }
        Double evictions = sum(registry.find("cache.evictions").tag("cache", name).meters());
        long requests = (long) (hits + misses);
        return new StatsReport(requests, hits.longValue(), misses.longValue(),
                requests == 0 ? null : hits / requests,
                evictions == null ? null : evictions.longValue());
    }

    /** Suma la primera medida de cada meter (contador, function counter o gauge); {@code null} si no hay ninguno. */
    private static @Nullable Double sum(Collection<Meter> meters) {
        if (meters.isEmpty()) {
            return null;
        }
        double total = 0;
        for (Meter meter : meters) {
            for (Measurement measurement : meter.measure()) {
                total += measurement.getValue();
                break;
            }
        }
        return total;
    }

    /** Respuesta de {@code GET /actuator/catalogcaches}. */
    public record CatalogCachesReport(String provider, SettingsReport defaults, CacheReport catalogTypes,
                                      Map<String, TypeReport> types) {
    }

    /** Un tipo: su configuración efectiva y sus cachés ({@code catalogById:<type>}, {@code catalogLists:<type>}). */
    public record TypeReport(SettingsReport settings, Map<String, CacheReport> caches) {
    }

    /** Configuración efectiva (ya completada con los valores globales); duraciones en ISO-8601 ({@code PT10M}). */
    public record SettingsReport(boolean enabled, @Nullable Long maximumSize, @Nullable String expireAfterWrite,
                                 @Nullable String expireAfterAccess, boolean stats, Map<String, String> nativeOptions) {

        static SettingsReport of(CacheSettings s) {
            return new SettingsReport(s.isEnabled(), s.getMaximumSize(), iso(s.getExpireAfterWrite()),
                    iso(s.getExpireAfterAccess()), s.isStats(),
                    s.getNativeOptions() == null ? Map.of() : Map.copyOf(s.getNativeOptions()));
        }

        private static @Nullable String iso(@Nullable Duration duration) {
            return duration == null ? null : duration.toString();
        }
    }

    /**
     * Estado de una caché. {@code active=false} si está desactivada por configuración (no existe).
     * {@code size} es el de Micrometer (aproximado según la librería); {@code null} si no se publica.
     */
    public record CacheReport(boolean active, @Nullable Long size, @Nullable StatsReport stats) {
    }

    /**
     * Estadísticas acumuladas (Micrometer). {@code hitRate} entre 0 y 1, {@code null} si aún no hubo
     * peticiones. {@code null} entero si la caché no registra estadísticas ({@code stats: false}).
     */
    public record StatsReport(long requests, long hits, long misses, @Nullable Double hitRate, @Nullable Long evictions) {
    }
}
