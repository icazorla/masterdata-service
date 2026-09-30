package com.ej1.app.common.catalog.infrastructure.persistence;

import com.ej1.app.common.catalog.domain.CatalogType;

import java.util.List;

/**
 * Qué par concreto de entidades JPA respalda a un {@link CatalogType}, más los nombres
 * de sus campos adicionales propios de ese tipo ({@code extraFields}, p. ej.
 * {@code "website"} solo para {@code brand} — ver {@code CatalogProperties.TypeEntry}).
 * Este es el único lugar de la capa de persistencia que todavía nombra directamente
 * {@code BrandEntity}/{@code CategoryEntity} — {@link CatalogRepositoryAdapter}
 * opera sobre cada tipo únicamente a través de los contratos estructurales
 * {@link CatalogEntity}/{@link CatalogTranslationEntity} más estos tokens
 * {@link Class} (para búsquedas en {@code EntityManager}, instanciación reflexiva
 * y JPQL construido a partir de {@link Class#getSimpleName()}) y {@code extraFields}
 * (para {@link CatalogExtraFieldsAccessor}, que resuelve cada nombre contra el getter/
 * setter de JavaBean real de la entidad — este record no sabe de qué tipo son ni cómo
 * se llaman sus accesores, solo qué nombres existen para este tipo).
 *
 * <p>Registrado por cada {@link CatalogType} en {@code CatalogPersistenceConfig} —
 * añadir un nuevo tipo de catálogo implica añadir sus dos clases {@code @Entity}
 * (inevitable: JPA necesita una clase concreta por tabla) y una entrada más ahí, no
 * una nueva clase de repositorio/mapeador/adaptador; añadir un campo adicional a un
 * tipo ya existente implica solo una columna más en su entidad y un nombre más en
 * {@code extraFields}.
 */
public record CatalogTypeDescriptor<E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>>(
        CatalogType type,
        Class<E> entityClass,
        Class<TE> translationEntityClass,
        List<String> extraFields
) {
}
