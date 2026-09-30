package com.ej1.app.common.catalog.domain.exception;

import com.ej1.app.common.domain.exception.InvalidQueryException;

import java.util.Set;

/**
 * La consulta es sintácticamente válida pero {@code sortBy} no nombra un campo ordenable de
 * catálogo. Se mapea a {@code 422 Unprocessable Entity} — ver {@link InvalidQueryException}.
 */
public class InvalidCatalogQueryException extends InvalidQueryException {

    /**
     * Coincide con la forma de la fábrica de excepciones de
     * {@link com.ej1.app.common.domain.SortableFieldValidator}, de modo que
     * {@code InvalidCatalogQueryException::new} pueda usarse allí como method reference.
     */
    public InvalidCatalogQueryException(String sortBy, Set<String> allowedFields) {
        super("sortBy '" + sortBy + "' is not sortable. Allowed values: " + allowedFields,
                "error.query.invalidSortField", sortBy, allowedFields);
    }
}
