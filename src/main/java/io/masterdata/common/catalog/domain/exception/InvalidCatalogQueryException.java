package io.masterdata.common.catalog.domain.exception;

import io.masterdata.common.domain.exception.InvalidQueryException;

import java.util.Collection;
import java.util.Set;

/**
 * La consulta es sintácticamente válida pero no tiene sentido para este catálogo: {@code sortBy}
 * no nombra un campo ordenable, o un filtro de campo adicional nombra un campo inexistente, usa
 * un operador que no aplica a su tipo o trae un valor que no encaja con él. Se mapea a
 * {@code 422 Unprocessable Entity} — ver {@link InvalidQueryException}.
 */
public class InvalidCatalogQueryException extends InvalidQueryException {

    /**
     * Coincide con la forma de la fábrica de excepciones de
     * {@link io.masterdata.common.domain.SortableFieldValidator}, de modo que
     * {@code InvalidCatalogQueryException::new} pueda usarse allí como method reference.
     */
    public InvalidCatalogQueryException(String sortBy, Set<String> allowedFields) {
        super("sortBy '" + sortBy + "' is not sortable. Allowed values: " + allowedFields,
                "error.query.invalidSortField", sortBy, allowedFields);
    }

    private InvalidCatalogQueryException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }

    /** {@code field} no es un campo común filtrable ni un campo adicional de este tipo. */
    public static InvalidCatalogQueryException unknownFilterField(String field, Collection<String> allowedFields) {
        return new InvalidCatalogQueryException(
                "'" + field + "' is not a filterable field. Allowed values: " + allowedFields,
                "error.query.unknownFilterField", field, allowedFields);
    }

    /** {@code field} es filtrable en general, pero no en esta API (p. ej. {@code enabled} en la de consulta). */
    public static InvalidCatalogQueryException filterNotAllowed(String field) {
        return new InvalidCatalogQueryException(
                "'" + field + "' cannot be filtered in this API",
                "error.query.filterNotAllowed", field);
    }

    /** Una condición de {@code filter} sin la forma {@code campo:valor}. */
    public static InvalidCatalogQueryException malformedFilter(String condition) {
        return new InvalidCatalogQueryException(
                "Malformed filter condition '" + condition + "': expected field:value",
                "error.query.malformedFilter", condition);
    }

    /** {@code key} ({@code campo.sufijo}) lleva un sufijo que no es ningún operador. */
    public static InvalidCatalogQueryException unknownFilterOperator(String key, String suffix) {
        return new InvalidCatalogQueryException(
                "Unknown filter operator '" + suffix + "' in '" + key + "'. Allowed values: [gt, gte, lt, lte]",
                "error.query.unknownFilterOperator", key, suffix);
    }

    /** Un operador de rango ({@code gte}, ...) sobre un campo cuyo tipo no es ordenable. */
    public static InvalidCatalogQueryException unsupportedFilterOperator(String key, String dataType) {
        return new InvalidCatalogQueryException(
                "Filter '" + key + "' is not valid for a field of type " + dataType,
                "error.query.unsupportedFilterOperator", key, dataType);
    }

    /** Un operador de rango con varios valores separados por comas. */
    public static InvalidCatalogQueryException multipleRangeValues(String key) {
        return new InvalidCatalogQueryException(
                "Filter '" + key + "' accepts a single value",
                "error.query.multipleRangeValues", key);
    }

    /** {@code value} no se puede convertir al tipo del campo filtrado. */
    public static InvalidCatalogQueryException invalidFilterValue(String field, String value, String dataType) {
        return new InvalidCatalogQueryException(
                "Value '" + value + "' is not a valid " + dataType + " for filter '" + field + "'",
                "error.query.invalidFilterValue", field, value, dataType);
    }
}
