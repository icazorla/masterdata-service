package com.ej1.app.common.catalog.domain;

import java.util.List;

/**
 * Un {@link CatalogType} configurado junto con la definición de todos sus campos: primero
 * los comunes a todo catálogo y después los adicionales propios de ese tipo, en el orden
 * de {@code app.catalog.types.<type>.extra-fields}.
 */
public record CatalogTypeDefinition(CatalogType type, List<CatalogField> fields) {
}
