package io.masterdata.common.catalog.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato estructural que ya satisface la entidad JPA padre de cada tipo de catálogo
 * ({@code BrandEntity}, {@code CategoryEntity}, ...) mediante sus propios accesores
 * generados por Lombok y su superclase {@link io.masterdata.common.infrastructure.persistence.AuditableEntity}.
 * Declarar {@code implements CatalogEntity} no añade código nuevo a esas
 * clases — solo permite que {@link CatalogRepositoryAdapter} opere sobre
 * cualquiera de ellas de forma genérica.
 */
public interface CatalogEntity {

    Long getId();

    void setId(Long id);

    /** Traducciones por locale, guardadas en la tabla propia del tipo (ver {@link CatalogImplicitNamingStrategy}). */
    List<CatalogTranslationValue> getTranslations();

    /** Peso para ordenación manual — a diferencia de name/description, no se traduce por locale. */
    Integer getWeight();

    void setWeight(Integer weight);

    Boolean getEnabled();

    void setEnabled(Boolean enabled);

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
