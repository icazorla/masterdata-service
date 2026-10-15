package io.masterdata.common.catalog.infrastructure.cache;

import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.service.CatalogCacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.support.NoOpCache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Traduce el nombre base de cada anotación de caché de catálogo ({@link CatalogCacheNames#BY_ID},
 * {@link CatalogCacheNames#LISTS}) a la caché del tipo de la llamada ({@code catalogById:province}…),
 * tomando el {@link CatalogType} de los argumentos del método: un parámetro {@code CatalogType}
 * ({@code findById}, {@code findAll}, {@code update}, {@code delete}) o el {@code type()} de un
 * {@link CreateCatalogCommand} ({@code create}). Los nombres que no son por tipo
 * ({@link CatalogCacheNames#TYPES}) se resuelven tal cual.
 *
 * <p>Una caché desactivada por configuración ({@code enabled: false}, global o de un tipo — ver
 * {@code CatalogCacheConfig}) se resuelve a un {@link NoOpCache}: las lecturas van siempre al
 * repositorio y las invalidaciones no hacen nada. Cualquier otra caché que no exista es un error de
 * programación (tipo no configurado o anotación con un nombre desconocido) y falla en vez de dejar
 * de cachear en silencio.
 */
public class CatalogCacheResolver implements CacheResolver {

    private static final Set<String> PER_TYPE = Set.of(CatalogCacheNames.PER_TYPE);

    private final CacheManager cacheManager;
    private final Set<String> disabledCaches;

    /** {@code disabledCaches}: nombres reales (p. ej. {@code catalogById:region}) de las cachés desactivadas. */
    public CatalogCacheResolver(CacheManager cacheManager, Set<String> disabledCaches) {
        this.cacheManager = cacheManager;
        this.disabledCaches = Set.copyOf(disabledCaches);
    }

    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        return context.getOperation().getCacheNames().stream()
                .map(baseName -> resolve(baseName, context))
                .toList();
    }

    private Cache resolve(String baseName, CacheOperationInvocationContext<?> context) {
        String name = PER_TYPE.contains(baseName) ? CatalogCacheNames.forType(baseName, typeOf(context).code()) : baseName;
        if (disabledCaches.contains(name)) {
            return new NoOpCache(name);
        }
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("No catalog cache named '" + name + "' (see CatalogCacheConfig)");
        }
        return cache;
    }

    private static CatalogType typeOf(CacheOperationInvocationContext<?> context) {
        return Arrays.stream(context.getArgs())
                .map(arg -> arg instanceof CreateCatalogCommand cmd ? cmd.type() : arg)
                .filter(CatalogType.class::isInstance)
                .map(CatalogType.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Per-type catalog cache on "
                        + context.getMethod() + " needs a CatalogType (or CreateCatalogCommand) argument"));
    }
}
