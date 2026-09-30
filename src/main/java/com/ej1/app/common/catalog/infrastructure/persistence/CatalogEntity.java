package com.ej1.app.common.catalog.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato estructural que ya satisface la entidad JPA padre de cada tipo de catálogo
 * ({@code BrandEntity}, {@code CategoryEntity}, ...) mediante sus propios accesores
 * generados por Lombok y su superclase {@link com.ej1.app.common.infrastructure.persistence.AuditableEntity}.
 * Declarar {@code implements CatalogEntity<TE>} no añade código nuevo a esas
 * clases — solo permite que {@link AbstractCatalogRepositoryAdapter} opere sobre
 * cualquiera de ellas de forma genérica.
 */
public interface CatalogEntity<TE extends CatalogTranslationEntity<?>> {

    Long getId();

    void setId(Long id);

    List<TE> getTranslations();

    /** Peso para ordenación manual — a diferencia de name/description, no se traduce por locale. */
    Integer getWeight();

    void setWeight(Integer weight);

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
