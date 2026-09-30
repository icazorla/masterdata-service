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
 * El {@code name}/{@code description} de un {@link CategoryEntity} para un locale.
 * {@code (locale, name)} es único a través de todas las categorías, no solo dentro
 * de una — reflejando el único {@code name} global que el {@code CategoryEntity}
 * previo a la internacionalización solía imponer, solo que ahora acotado por
 * idioma.
 */
@Entity
@Table(name = "category_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"locale", "name"}))
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTranslationEntity extends CatalogTranslationBaseEntity implements CatalogTranslationEntity<CategoryEntity> {

    /**
     * Lado propietario de la asociación (la FK {@code category_id} vive aquí). Excluido de
     * {@code equals}/{@code hashCode}/{@code toString} para evitar recursión de vuelta a
     * {@link CategoryEntity#getTranslations()}.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Override
    public void linkParent(CategoryEntity parent) {
        this.category = parent;
    }
}
