package io.masterdata.common.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El {@code name}/{@code description} de una entrada de catálogo para un locale: una fila
 * de la tabla de traducciones de su tipo ({@code brand_translations}, {@code category_translations},
 * ...). Es un valor ({@code @Embeddable}), no una entidad — no tiene id propio y solo existe
 * dentro de su padre — así que una única clase sirve a todos los tipos de catálogo: la
 * {@code @ElementCollection translations} de {@link CatalogBaseEntity} la usa, y
 * {@link CatalogImplicitNamingStrategy} decide en qué tabla y con qué FK se guarda.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogTranslationValue {

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;
}
