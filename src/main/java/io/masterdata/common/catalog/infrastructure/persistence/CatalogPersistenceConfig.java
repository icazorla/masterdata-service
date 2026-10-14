package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Construye el registro {@code CatalogType -> CatalogTypeDescriptor} sobre el que
 * despacha {@link CatalogRepositoryAdapter}, a partir de {@link CatalogProperties}
 * ({@code app.catalog.types.*} en {@code application.yml}) en lugar de un mapa Java
 * de literales {@code Class} escrito a mano. Añadir un nuevo tipo de catálogo pasa a
 * ser puramente un cambio de configuración + una nueva clase {@code @Entity} — sin
 * ninguna clase Java aquí que tocar. También vuelca esas mismas claves en
 * {@link CatalogType#configure}, de modo que {@link CatalogType#of}/
 * {@link CatalogType#values()} reflejan exactamente {@code app.catalog.types}.
 *
 * <p>La contrapartida: {@code entity-class} es una
 * simple cadena resueltas mediante {@link Class#forName}, por lo que un error
 * tipográfico solo aparece como un fallo de arranque ({@link IllegalStateException})
 * en vez de un error de compilación — a diferencia de la versión anterior con
 * {@code Map.of(...)} escrito a mano, donde {@code BrandEntity.class} no podía
 * apuntar mal a una clase inexistente. {@link #toDescriptor} hace un único cast
 * sin comprobar, acotado, por cada entrada, para restaurar el tipo {@code <E>} que
 * {@link Class#forName} necesariamente pierde.
 *
 * <p>Los campos adicionales de cada tipo no se configuran: {@link #discoverExtraFields}
 * los deduce al arrancar del metamodelo JPA de su entidad, así que añadir uno es solo
 * añadir la columna a la entidad.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogPersistenceConfig {

    private final CatalogProperties properties;

    public CatalogPersistenceConfig(CatalogProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Map<CatalogType, CatalogTypeDescriptor<?>> catalogTypeDescriptors(EntityManagerFactory entityManagerFactory) {
        CatalogType.configure(properties.getTypes().keySet());
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        Map<CatalogType, CatalogTypeDescriptor<?>> descriptors = new LinkedHashMap<>();
        properties.getTypes().forEach((key, entry) -> {
            CatalogType type = CatalogType.of(key);
            descriptors.put(type, toDescriptor(type, entry, metamodel));
        });
        return Collections.unmodifiableMap(descriptors);
    }

    @SuppressWarnings("unchecked")
    private <E extends CatalogEntity> CatalogTypeDescriptor<E> toDescriptor(
            CatalogType type, CatalogProperties.TypeEntry entry, Metamodel metamodel) {
        Class<E> entityClass = (Class<E>) loadClass(entry.getEntityClass());
        List<String> extraFields = discoverExtraFields(metamodel, entityClass);
        // Fail-fast: comprueba ya el par getter/setter de cada campo, no en la primera petición.
        CatalogExtraFieldsAccessor.dataTypes(entityClass, extraFields);
        // Fail-fast: en el parámetro filter un campo adicional se nombra igual que los comunes
        // filtrables, así que no puede llamarse como uno de ellos (name, description, enabled).
        extraFields.stream()
                .filter(CatalogFilter.COMMON_FIELDS::contains)
                .findFirst()
                .ifPresent(field -> {
                    throw new IllegalStateException("Extra field '" + field + "' of " + entityClass.getSimpleName()
                            + " clashes with a common filterable catalog field " + CatalogFilter.COMMON_FIELDS);
                });
        return new CatalogTypeDescriptor<>(type, entityClass, extraFields);
    }

    /**
     * Los campos adicionales propios de {@code entityClass}: sus atributos {@code BASIC}
     * (columnas simples) declarados en la propia entidad — no los heredados de
     * {@link CatalogBaseEntity} ({@code id}, {@code weight}, {@code enabled}, timestamps),
     * ni relaciones como {@code translations}, ni {@code @Transient}. Los nombres son los
     * del metamodelo, es decir, los mismos que usa JPQL para {@code sortBy}.
     *
     * <p>{@code getDeclaredSingularAttributes()} no garantiza orden, así que se devuelven en
     * el orden de declaración en la clase — el que se ve en {@code GET /catalog-types}.
     */
    static List<String> discoverExtraFields(Metamodel metamodel, Class<?> entityClass) {
        Set<String> names = metamodel.entity(entityClass).getDeclaredSingularAttributes().stream()
                .filter(a -> a.getPersistentAttributeType() == Attribute.PersistentAttributeType.BASIC)
                .filter(a -> !a.isId() && !a.isVersion())
                .map(Attribute::getName)
                .collect(Collectors.toSet());
        return Arrays.stream(entityClass.getDeclaredFields())
                .map(Field::getName)
                .filter(names::contains)
                .toList();
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Configured catalog entity class not found: " + className, e);
        }
    }
}
