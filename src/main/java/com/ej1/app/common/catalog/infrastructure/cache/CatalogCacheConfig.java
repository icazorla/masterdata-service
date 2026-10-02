package com.ej1.app.common.catalog.infrastructure.cache;

import com.ej1.app.common.catalog.domain.service.CatalogCacheNames;
import com.ej1.app.common.catalog.infrastructure.cache.caffeine.CaffeineCacheProviderConfig;
import com.ej1.app.common.catalog.infrastructure.cache.spi.CacheSpec;
import com.ej1.app.common.catalog.infrastructure.cache.spi.CatalogCacheProvider;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogProperties.CacheSettings;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Caché de lecturas de catálogo, independiente de la librería de caché — ver {@link CatalogCacheNames}
 * para qué se cachea y {@code CatalogQueryService}/{@code CatalogCommandService} para dónde.
 *
 * <ul>
 *   <li><b>La librería es un detalle enchufable</b>: cada caché la construye el
 *       {@link CatalogCacheProvider} activo ({@code app.catalog.cache.provider}, hoy solo
 *       {@code caffeine} — ver {@link CaffeineCacheProviderConfig}) a partir de un {@link CacheSpec}
 *       neutro. Esta clase solo las reúne en un {@link SimpleCacheManager} de Spring. Para otra
 *       librería: un adaptador nuevo en su subpaquete, importado aquí — ver {@link CatalogCacheProvider}.</li>
 *   <li><b>Una caché por tipo y configuración por tipo</b>: {@code catalogById:<type>} y
 *       {@code catalogLists:<type>} con la configuración de {@code app.catalog.types.<type>.cache},
 *       heredando campo a campo de {@code app.catalog.cache} lo que no indique (ver
 *       {@link CacheSettings#effectiveFor}). Un tipo con {@code enabled: false} no tiene cachés:
 *       {@link CatalogCacheResolver} le da un {@code NoOpCache}. {@code catalogTypes} usa la global.</li>
 *   <li><b>Acotada</b>: tamaño máximo y caducidad. La caducidad limita cuánto tiempo puede servirse un
 *       dato obsoleto si la base de datos cambia por fuera de esta instancia (otra réplica, un script
 *       SQL…), ya que la invalidación de los comandos es local a cada proceso. Una configuración que el
 *       proveedor no acepte hace fallar el arranque.</li>
 *   <li><b>Consciente de la transacción</b>: el {@link TransactionAwareCacheManagerProxy} retrasa
 *       los {@code evict}/{@code clear} de los comandos hasta el commit — así un rollback (p. ej.
 *       un {@code delete} que falla por FK al hacer flush) no vacía la caché, y una lectura
 *       concurrente no puede recachear el valor antiguo entre la invalidación y el commit.</li>
 * </ul>
 *
 * <p>Estado en Actuator: {@code /actuator/catalogcaches} ({@link CatalogCachesEndpoint}: configuración
 * efectiva y estadísticas por tipo, vía Micrometer) y el estándar {@code /actuator/caches}.
 *
 * <p>El gestor es estático: solo existen las cachés creadas aquí. Declarar aquí el
 * {@link CacheManager} hace que la autoconfiguración de caché de Spring Boot se retire, así que
 * {@code spring.cache.*} no aplica a estas cachés.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CatalogProperties.class)
@Import(CaffeineCacheProviderConfig.class)
public class CatalogCacheConfig {

    @Bean
    public CacheManager cacheManager(CatalogProperties properties, ObjectProvider<CatalogCacheProvider> providers) {
        CatalogCacheProvider provider = requireProvider(properties.getCache().getProvider(), providers);
        List<Cache> caches = cacheSettings(properties).entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(e -> provider.create(e.getKey(), toSpec(e.getValue())))
                .toList();
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        manager.afterPropertiesSet();  // no es un bean (va envuelto): hay que inicializarlo a mano
        return new TransactionAwareCacheManagerProxy(manager);
    }

    @Bean(CatalogCacheNames.RESOLVER)
    public CatalogCacheResolver catalogCacheResolver(CacheManager cacheManager, CatalogProperties properties) {
        Set<String> disabled = cacheSettings(properties).entrySet().stream()
                .filter(e -> !e.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return new CatalogCacheResolver(cacheManager, disabled);
    }

    /** Estado de las cachés en Actuator: {@code /actuator/catalogcaches} (ver {@link CatalogCachesEndpoint}). */
    @Bean
    public CatalogCachesEndpoint catalogCachesEndpoint(CatalogProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        return new CatalogCachesEndpoint(properties, meterRegistry);
    }

    /**
     * Configuración efectiva de cada caché, por su nombre real: {@code catalogTypes} con la
     * global, y {@code catalogById:<type>}/{@code catalogLists:<type>} con la de su tipo completada
     * con la global.
     */
    static Map<String, CacheSettings> cacheSettings(CatalogProperties properties) {
        CacheSettings defaults = properties.getCache();
        Map<String, CacheSettings> settings = new LinkedHashMap<>();
        settings.put(CatalogCacheNames.TYPES, defaults);
        properties.getTypes().forEach((type, entry) -> {
            CacheSettings effective = entry.getCache().effectiveFor(defaults);
            for (String baseName : CatalogCacheNames.PER_TYPE) {
                settings.put(CatalogCacheNames.forType(baseName, type), effective);
            }
        });
        return settings;
    }

    static CacheSpec toSpec(CacheSettings settings) {
        return new CacheSpec(settings.getMaximumSize(), settings.getExpireAfterWrite(), settings.getExpireAfterAccess(),
                settings.isStats(), settings.getNativeOptions() == null ? Map.of() : settings.getNativeOptions());
    }

    private static CatalogCacheProvider requireProvider(String id, ObjectProvider<CatalogCacheProvider> providers) {
        return providers.orderedStream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No catalog cache provider '" + id
                        + "' available (app.catalog.cache.provider). Available: "
                        + providers.orderedStream().map(CatalogCacheProvider::id).toList()
                        + " — is its library on the classpath?"));
    }
}
