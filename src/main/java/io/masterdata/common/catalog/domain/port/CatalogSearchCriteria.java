package io.masterdata.common.catalog.domain.port;

import java.util.ArrayList;
import java.util.List;

/**
 * Criterios de filtrado para listar entradas de un catálogo: una lista de {@link CatalogFilter}
 * combinados todos con AND (ver su sintaxis en el parámetro {@code filter}). Vacía no filtra.
 *
 * <p>{@code name}/{@code description} se evalúan en base de datos sobre la traducción resuelta
 * para el locale del cliente (con fallback al idioma por defecto cuando ese locale no tiene
 * traducción) — ver {@code CatalogRepository#findAll}; {@code enabled} y los campos adicionales
 * viven en la propia entrada y no dependen del idioma.
 */
public record CatalogSearchCriteria(List<CatalogFilter> filters) {

    public CatalogSearchCriteria {
        filters = filters == null ? List.of() : List.copyOf(filters);
    }

    public static CatalogSearchCriteria none() {
        return new CatalogSearchCriteria(List.of());
    }

    /** Estos criterios más {@code filter} (con AND). */
    public CatalogSearchCriteria and(CatalogFilter filter) {
        List<CatalogFilter> combined = new ArrayList<>(filters);
        combined.add(filter);
        return new CatalogSearchCriteria(combined);
    }

    /** Si algún filtro es sobre {@code field}. */
    public boolean filtersBy(String field) {
        return filters.stream().anyMatch(f -> f.field().equals(field));
    }

    /**
     * Si solo filtra por estado ({@code enabled}) o por nada: la llamada típica para poblar
     * selects, la única que se cachea.
     */
    public boolean filtersOnlyByEnabled() {
        return filters.stream().allMatch(f -> f.field().equals(CatalogFilter.ENABLED));
    }
}
