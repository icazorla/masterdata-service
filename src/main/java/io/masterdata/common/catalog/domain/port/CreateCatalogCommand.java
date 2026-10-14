package io.masterdata.common.catalog.domain.port;

import io.masterdata.common.catalog.domain.CatalogType;

import java.util.List;
import java.util.Map;

/**
 * Objeto de comando que representa la intención de crear una nueva entrada de
 * catálogo de un {@link CatalogType} dado (marca, categoría, ...).
 * Forma parte del puerto de entrada del dominio — independiente de cualquier
 * DTO de transporte.
 *
 * <p>{@code extraFields} lleva los campos propios de {@code type} (p. ej. {@code website}
 * solo para {@code brand}), indexados por nombre — ver el javadoc de {@code Catalog}.
 */
public record CreateCatalogCommand(
        CatalogType type,
        int weight,
        boolean enabled,
        Map<String, Object> extraFields,
        List<CatalogTranslationCommand> translations
) {
}
