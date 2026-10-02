package com.ej1.app.common.catalog.infrastructure.cache.spi;

import org.springframework.cache.Cache;

/**
 * Puerto entre la caché de catálogos y la librería de caché concreta (Caffeine, cache2k, Ehcache…).
 * Es lo <em>único</em> que conoce la librería: {@code CatalogCacheConfig} (montaje del
 * {@code CacheManager}), {@code CatalogCacheResolver}, {@code CatalogCachesEndpoint} y el dominio
 * trabajan solo con la abstracción de Spring ({@link Cache}) y con {@link CacheSpec}.
 *
 * <p>Para añadir una librería: implementar esta interfaz en un subpaquete propio de
 * {@code infrastructure.cache} (como {@code caffeine/}), registrarla en una {@code @Configuration}
 * con {@code @ConditionalOnClass(<librería>)} + {@code @ConditionalOnProperty(app.catalog.cache.provider=<id>)},
 * importarla desde {@code CatalogCacheConfig} y añadir la dependencia (opcional) al {@code pom.xml}.
 *
 * <p>Contrato de {@link #create}:
 * <ul>
 *   <li>Debe respetar todo {@link CacheSpec}; una opción que la librería no soporte (o una clave de
 *       {@code nativeOptions} que no entienda) debe <b>fallar</b> (excepción → la app no arranca),
 *       nunca ignorarse en silencio.</li>
 *   <li>Debe devolver la implementación de {@link Cache} estándar de Spring para esa librería
 *       (p. ej. {@code CaffeineCache}, {@code JCacheCache}), sin envolver: es la que Spring Boot sabe
 *       conectar a Micrometer, de donde {@code CatalogCachesEndpoint} saca las estadísticas.</li>
 *   <li>No debe admitir valores {@code null} (los métodos cacheados nunca devuelven {@code null}).</li>
 *   <li>Los valores se cachean por referencia (los {@code Catalog} son inmutables y no son
 *       {@code Serializable}): una librería que copie por valor — p. ej. JCache por defecto — debe
 *       configurarse por referencia.</li>
 * </ul>
 */
public interface CatalogCacheProvider {

    /** Identificador del proveedor, el valor de {@code app.catalog.cache.provider} (p. ej. {@code caffeine}). */
    String id();

    /** Crea la caché {@code name} con la configuración {@code spec}. */
    Cache create(String name, CacheSpec spec);
}
