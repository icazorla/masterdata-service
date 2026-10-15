package io.masterdata.common.catalog.infrastructure.persistence;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula {@code app.catalog.types.<type>} desde {@code application.yml} — el nombre
 * de clase completamente cualificado de la entidad {@code @Entity} que respalda
 * cada {@link io.masterdata.common.catalog.domain.CatalogType}. {@link CatalogPersistenceConfig}
 * resuelve los nombres de clase de cada entrada mediante reflexión para construir el
 * registro real de {@code CatalogTypeDescriptor}, de modo que añadir un tipo de catálogo
 * es un cambio de configuración más su clase {@code @Entity}, nunca una nueva clase
 * Java de registro. Los campos adicionales propios de cada tipo (p. ej. {@code regionCode} en
 * {@code province}) no se configuran aquí: {@link CatalogPersistenceConfig#discoverExtraFields}
 * los deduce de las columnas de la entidad. {@code names} lleva el
 * nombre traducido del tipo por idioma, usado en los mensajes de error (ver
 * {@code CatalogTypeNamesMessageSource}).
 *
 * <p>{@code cache} (global, {@code app.catalog.cache}) y {@code types.<type>.cache} configuran
 * la caché de lecturas (ver {@code CatalogCacheConfig}) con campos neutros respecto a la librería:
 * cada tipo tiene sus propias cachés y cada campo que su {@code cache} no indique se hereda del
 * {@code cache} global.
 */
@ConfigurationProperties(prefix = "app.catalog")
public class CatalogProperties {

    private Map<String, TypeEntry> types = new LinkedHashMap<>();

    private CacheDefaults cache = new CacheDefaults();

    public Map<String, TypeEntry> getTypes() {
        return types;
    }

    public void setTypes(Map<String, TypeEntry> types) {
        this.types = types;
    }

    /** Valores por defecto de caché para todos los tipos, el proveedor, y la configuración de la caché global {@code catalogTypes}. */
    public CacheDefaults getCache() {
        return cache;
    }

    public void setCache(CacheDefaults cache) {
        this.cache = cache;
    }

    /**
     * Configuración de caché, neutra respecto a la librería (la traduce el {@code CatalogCacheProvider}
     * activo). En un tipo todos los campos son opcionales: {@code null} hereda el valor global
     * <em>campo a campo</em> (ver {@link #effectiveFor(CacheSettings)}).
     */
    public static class CacheSettings {

        /** {@code false} desactiva la caché (sin cachear nada) — en un tipo, solo para ese tipo. */
        private Boolean enabled;

        /** Número máximo de entradas; {@code null} = sin límite. */
        private Long maximumSize;

        /** Caducidad desde que se escribe la entrada (p. ej. {@code 10m}); {@code null} = no caduca por escritura. */
        private Duration expireAfterWrite;

        /** Caducidad desde el último acceso; {@code null} = no caduca por inactividad. */
        private Duration expireAfterAccess;

        /** Registrar estadísticas (aciertos/fallos/expulsiones) para Actuator/Micrometer. */
        private Boolean stats;

        /**
         * Vía de escape: opciones propias de la librería, que el proveedor interpreta (Caffeine:
         * claves de {@code CaffeineSpec}, p. ej. {@code initialCapacity: 64}). Se fusionan con las
         * globales (las del tipo ganan). Un proveedor que no entienda una clave falla al arrancar.
         */
        private Map<String, String> nativeOptions;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(Long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }

        public Boolean getStats() {
            return stats;
        }

        public void setStats(Boolean stats) {
            this.stats = stats;
        }

        public Map<String, String> getNativeOptions() {
            return nativeOptions;
        }

        public void setNativeOptions(Map<String, String> nativeOptions) {
            this.nativeOptions = nativeOptions;
        }

        /**
         * Esta configuración (de un tipo) completada campo a campo con {@code defaults} (la global):
         * cada campo {@code null} toma el valor global; {@code nativeOptions} se fusiona (gana el tipo).
         */
        public CacheSettings effectiveFor(CacheSettings defaults) {
            CacheSettings effective = new CacheSettings();
            effective.enabled = enabled != null ? enabled : defaults.enabled;
            effective.maximumSize = maximumSize != null ? maximumSize : defaults.maximumSize;
            effective.expireAfterWrite = expireAfterWrite != null ? expireAfterWrite : defaults.expireAfterWrite;
            effective.expireAfterAccess = expireAfterAccess != null ? expireAfterAccess : defaults.expireAfterAccess;
            effective.stats = stats != null ? stats : defaults.stats;
            Map<String, String> options = new LinkedHashMap<>();
            if (defaults.nativeOptions != null) {
                options.putAll(defaults.nativeOptions);
            }
            if (nativeOptions != null) {
                options.putAll(nativeOptions);
            }
            effective.nativeOptions = options;
            return effective;
        }

        public boolean isEnabled() {
            return !Boolean.FALSE.equals(enabled);
        }

        public boolean isStats() {
            return Boolean.TRUE.equals(stats);
        }
    }

    /**
     * {@code app.catalog.cache}: los valores por defecto (los que hereda cada tipo) más el proveedor,
     * que es único para toda la app. Valores de serie: Caffeine, activada, 1000 entradas, 10 min de
     * caducidad por escritura y estadísticas.
     */
    public static class CacheDefaults extends CacheSettings {

        /** Proveedor de caché: el {@code id()} de un {@code CatalogCacheProvider} disponible. */
        private String provider = "caffeine";

        public CacheDefaults() {
            setEnabled(true);
            setMaximumSize(1000L);
            setExpireAfterWrite(Duration.ofMinutes(10));
            setStats(true);
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class TypeEntry {

        private String entityClass;
        private Map<String, String> names = new LinkedHashMap<>();
        private CacheSettings cache = new CacheSettings();

        public String getEntityClass() {
            return entityClass;
        }

        public void setEntityClass(String entityClass) {
            this.entityClass = entityClass;
        }

        /** Nombre traducido del tipo por idioma (etiqueta BCP 47, p. ej. {@code en}, {@code es}) — ver {@code CatalogTypeNamesMessageSource}. */
        public Map<String, String> getNames() {
            return names;
        }

        public void setNames(Map<String, String> names) {
            this.names = names;
        }

        /** Caché propia de este tipo; lo que no indique se hereda de {@code app.catalog.cache}. */
        public CacheSettings getCache() {
            return cache;
        }

        public void setCache(CacheSettings cache) {
            this.cache = cache;
        }
    }
}
