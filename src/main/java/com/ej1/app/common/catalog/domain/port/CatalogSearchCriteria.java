package com.ej1.app.common.catalog.domain.port;

/**
 * Criterios de filtrado para listar entradas de un catálogo. {@code name}/{@code description}
 * coinciden sin distinguir mayúsculas como subcadena, evaluados en base de datos sobre la
 * traducción resuelta para el locale del cliente (con fallback al idioma por defecto cuando
 * ese locale no tiene traducción) — ver {@code CatalogRepository#findAll}. Un valor
 * {@code null} no filtra por ese campo; cuando se dan ambos se combinan con OR (coincide
 * si el nombre <em>o</em> la descripción contienen el filtro), no con AND.
 */
public record CatalogSearchCriteria(String name, String description) {
}
