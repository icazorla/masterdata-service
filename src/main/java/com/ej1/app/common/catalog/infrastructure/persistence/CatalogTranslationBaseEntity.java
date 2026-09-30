package com.ej1.app.common.catalog.infrastructure.persistence;

import com.ej1.app.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base para las entidades JPA de traducción de un catálogo ({@code BrandTranslationEntity},
 * {@code CategoryTranslationEntity}, ...), que además del {@code id} de {@link BaseEntity}
 * llevan la misma tripleta {@code locale}/{@code name}/{@code description} — declarada aquí
 * una sola vez en lugar de repetirse por clase.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class CatalogTranslationBaseEntity extends BaseEntity {

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;
}
