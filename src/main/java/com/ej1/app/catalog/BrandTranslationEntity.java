package com.ej1.app.catalog;

import com.ej1.app.common.catalog.infrastructure.persistence.CatalogTranslationBaseEntity;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogTranslationEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * El {@code name}/{@code description} de un {@link BrandEntity} para un locale.
 * {@code (locale, name)} es único a través de todas las marcas, no solo dentro
 * de una — reflejando el único {@code name} global que el {@code BrandEntity}
 * previo a la internacionalización solía imponer, solo que ahora acotado por
 * idioma.
 */
@Entity
@Table(name = "brand_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"locale", "name"}))
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandTranslationEntity extends CatalogTranslationBaseEntity implements CatalogTranslationEntity<BrandEntity> {

    /**
     * Lado propietario de la asociación (la FK {@code brand_id} vive aquí). Excluido de
     * {@code equals}/{@code hashCode}/{@code toString} para evitar recursión de vuelta a
     * {@link BrandEntity#getTranslations()}.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

    @Override
    public void linkParent(BrandEntity parent) {
        this.brand = parent;
    }
}
