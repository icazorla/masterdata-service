package io.masterdata.common.catalog.domain;

import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * Atajo para los tests: los {@link CatalogSearchCriteria} que produce
 * {@code filter=name:<name>;description:<description>;enabled:<enabled>}, omitiendo los
 * {@code null} — en ese mismo orden, que es también el que deja la API de consulta al añadir
 * su {@code enabled:true}.
 */
public final class TestCriteria {

    private TestCriteria() {
    }

    public static CatalogSearchCriteria of(String name, String description, Boolean enabled) {
        List<CatalogFilter> filters = new ArrayList<>();
        if (name != null) {
            filters.add(new CatalogFilter(CatalogFilter.NAME, name));
        }
        if (description != null) {
            filters.add(new CatalogFilter(CatalogFilter.DESCRIPTION, description));
        }
        if (enabled != null) {
            filters.add(new CatalogFilter(CatalogFilter.ENABLED, enabled.toString()));
        }
        return new CatalogSearchCriteria(filters);
    }
}
