package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.CatalogType;

import java.util.List;

/**
 * Qué entidad JPA respalda a un {@link CatalogType}, más los nombres de sus campos
 * adicionales propios de ese tipo ({@code extraFields}, p. ej. {@code "website"} solo para
 * {@code brand} — ver {@code CatalogPersistenceConfig#discoverExtraFields}).
 * {@link CatalogRepositoryAdapter} opera sobre cada tipo únicamente a través del contrato
 * estructural {@link CatalogEntity} más este token {@link Class} (para búsquedas en
 * {@code EntityManager}, instanciación reflexiva y JPQL construido a partir de
 * {@link Class#getSimpleName()}) y {@code extraFields} (para {@link CatalogExtraFieldsAccessor},
 * que resuelve cada nombre contra el getter/setter de JavaBean real de la entidad).
 *
 * <p>Registrado por cada {@link CatalogType} en {@code CatalogPersistenceConfig} —
 * añadir un nuevo tipo de catálogo implica añadir su clase {@code @Entity} (con sus
 * traducciones como {@code @ElementCollection} de {@link CatalogTranslationValue}) y una
 * entrada más en {@code application.yml}, no una nueva clase de repositorio/mapeador/
 * adaptador; añadir un campo adicional a un tipo ya existente implica solo una columna
 * más en su entidad.
 */
public record CatalogTypeDescriptor<E extends CatalogEntity>(
        CatalogType type,
        Class<E> entityClass,
        List<String> extraFields
) {
}
