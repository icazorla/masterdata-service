package com.ej1.app.common.catalog.infrastructure.persistence;

/**
 * Contrato estructural que satisface la entidad JPA de traducción de cada tipo de
 * catálogo ({@code BrandTranslationEntity}, {@code CategoryTranslationEntity}, ...) —
 * {@code getLocale}/{@code setLocale}/{@code getName}/{@code setName}/{@code getDescription}/
 * {@code setDescription} ya están generados por Lombok {@code @Data}; solo
 * {@link #linkParent} se añade a mano (una línea, en sustitución de las llamadas
 * específicas por tipo {@code setBrand}/{@code setCategory} que antes había que
 * invocar directamente en la asociación del lado propietario), de modo que
 * {@link AbstractCatalogRepositoryAdapter} pueda enlazar una fila de traducción
 * recién construida con su padre sin saber de qué tipo de catálogo se trata.
 */
public interface CatalogTranslationEntity<E> {

    String getLocale();

    void setLocale(String locale);

    String getName();

    void setName(String name);

    String getDescription();

    void setDescription(String description);

    /** Enlaza la asociación del lado propietario de esta fila de traducción con {@code parent}. */
    void linkParent(E parent);
}
