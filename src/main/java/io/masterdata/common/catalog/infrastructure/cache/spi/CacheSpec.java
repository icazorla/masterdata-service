package io.masterdata.common.catalog.infrastructure.cache.spi;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * Configuración de una caché, neutra respecto a la librería: lo que {@link CatalogCacheProvider#create}
 * recibe para construirla. Es la configuración <em>efectiva</em> (la del tipo ya completada con la
 * global — ver {@code CatalogProperties.CacheSettings#effectiveFor}).
 *
 * @param maximumSize       número máximo de entradas; {@code null} = sin límite
 * @param expireAfterWrite  caducidad desde la escritura; {@code null} = no caduca por escritura
 * @param expireAfterAccess caducidad desde el último acceso; {@code null} = no caduca por inactividad
 * @param stats             registrar estadísticas (aciertos/fallos/expulsiones) para Micrometer
 * @param nativeOptions     opciones propias de la librería, que solo el proveedor interpreta
 */
public record CacheSpec(
        @Nullable Long maximumSize,
        @Nullable Duration expireAfterWrite,
        @Nullable Duration expireAfterAccess,
        boolean stats,
        Map<String, String> nativeOptions
) {
    public CacheSpec {
        nativeOptions = Map.copyOf(nativeOptions);
    }
}
