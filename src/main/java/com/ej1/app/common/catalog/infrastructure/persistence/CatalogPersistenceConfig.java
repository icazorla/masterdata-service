package com.ej1.app.common.catalog.infrastructure.persistence;

import com.ej1.app.common.catalog.domain.CatalogType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el registro {@code CatalogType -> CatalogTypeDescriptor} sobre el que
 * despacha {@link CatalogRepositoryAdapter}, a partir de {@link CatalogProperties}
 * ({@code app.catalog.types.*} en {@code application.yml}) en lugar de un mapa Java
 * de literales {@code Class} escrito a mano. Añadir un nuevo tipo de catálogo pasa a
 * ser puramente un cambio de configuración + dos nuevas clases {@code @Entity} — sin
 * ninguna clase Java aquí que tocar.
 *
 * <p>La contrapartida: {@code entity-class}/{@code translation-entity-class} son
 * simples cadenas resueltas mediante {@link Class#forName}, por lo que un error
 * tipográfico solo aparece como un fallo de arranque ({@link IllegalStateException})
 * en vez de un error de compilación — a diferencia de la versión anterior con
 * {@code Map.of(...)} escrito a mano, donde {@code BrandEntity.class} no podía
 * apuntar mal a una clase inexistente. {@link #toDescriptor} hace un único cast
 * sin comprobar, acotado, por cada entrada, para restaurar el emparejamiento
 * {@code <E, TE>} que {@link Class#forName} necesariamente pierde.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogPersistenceConfig {

    private final CatalogProperties properties;

    public CatalogPersistenceConfig(CatalogProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Map<CatalogType, CatalogTypeDescriptor<?, ?>> catalogTypeDescriptors() {
        Map<CatalogType, CatalogTypeDescriptor<?, ?>> descriptors = new LinkedHashMap<>();
        properties.getTypes().forEach((key, entry) -> {
            CatalogType type = CatalogType.of(key);
            descriptors.put(type, toDescriptor(type, entry));
        });
        return Collections.unmodifiableMap(descriptors);
    }

    @SuppressWarnings("unchecked")
    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> CatalogTypeDescriptor<E, TE> toDescriptor(
            CatalogType type, CatalogProperties.TypeEntry entry) {
        Class<E> entityClass = (Class<E>) loadClass(entry.getEntityClass());
        Class<TE> translationEntityClass = (Class<TE>) loadClass(entry.getTranslationEntityClass());
        return new CatalogTypeDescriptor<>(type, entityClass, translationEntityClass, List.copyOf(entry.getExtraFields()));
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Configured catalog entity class not found: " + className, e);
        }
    }
}
