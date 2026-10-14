package io.masterdata.common.catalog.domain;

import java.util.List;

/**
 * Un {@link CatalogType} configurado junto con la definición de todos sus campos: primero
 * los comunes a todo catálogo y después los adicionales propios de ese tipo, en el orden
 * en que se declaran en su entidad.
 */
public record CatalogTypeDefinition(CatalogType type, List<CatalogField> fields) {
}
