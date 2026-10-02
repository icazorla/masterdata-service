package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.CatalogType;

/**
 * Nombres de las cachés de catálogo. Las lecturas cacheadas viven en {@link CatalogQueryService}
 * ({@code @Cacheable}) y sus invalidaciones en {@link CatalogCommandService} ({@code @CacheEvict}).
 *
 * <p>{@link #BY_ID} y {@link #LISTS} son nombres <em>base</em>: cada {@link CatalogType} tiene sus
 * propias cachés ({@link #forType}, p. ej. {@code catalogById:brand}), con su propia configuración
 * ({@code app.catalog.types.<type>.cache}). Ambos servicios declaran {@link #RESOLVER} en
 * {@code @CacheConfig}: ese {@code CacheResolver} (infraestructura) traduce el nombre base de cada
 * anotación a la caché del tipo de la llamada. {@link #TYPES} es global, no por tipo.
 */
public final class CatalogCacheNames {

    /** Nombre del bean {@code CacheResolver} que eligen las cachés por tipo. */
    public static final String RESOLVER = "catalogCacheResolver";

    /** {@link CatalogQueryService#findTypeDefinitions()} — global; solo cambia al reiniciar (sale de {@code application.yml}). */
    public static final String TYPES = "catalogTypes";

    /** Base de {@link CatalogQueryService#findById}, por tipo; clave {@link #BY_ID_KEY}. */
    public static final String BY_ID = "catalogById";

    /** Base de {@link CatalogQueryService#findAll}, por tipo; solo listados sin filtro de texto (los de los selects). */
    public static final String LISTS = "catalogLists";

    /** Nombres base que tienen una caché por tipo. */
    public static final String[] PER_TYPE = {BY_ID, LISTS};

    /**
     * Clave SpEL de {@link #BY_ID}, compartida por el {@code @Cacheable} de la lectura y los
     * {@code @CacheEvict} de {@code update}/{@code delete}. Basta el id: la caché ya es la del tipo.
     */
    static final String BY_ID_KEY = "#id";

    /** Nombre real de la caché {@code baseName} de {@code type}, p. ej. {@code catalogById:brand}. */
    public static String forType(String baseName, CatalogType type) {
        return forType(baseName, type.code());
    }

    public static String forType(String baseName, String typeCode) {
        return baseName + ":" + typeCode;
    }

    private CatalogCacheNames() {
    }
}
