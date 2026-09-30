package com.ej1.app.product.domain.exception;

import com.ej1.app.common.domain.exception.InvalidQueryException;

import java.util.Set;

/**
 * La consulta es sintácticamente válida (contenido bien formado que coincide con el
 * tipo de medio declarado, cada campo bien formado individualmente) pero carece de
 * sentido semánticamente como consulta de producto — p. ej. {@code minPrice > maxPrice}.
 *
 * <p>Se mapea a {@code 422 Unprocessable Entity}, siguiendo la distinción que la RFC 10008
 * establece para el método HTTP QUERY: 400 para contenido que no coincide con el
 * tipo de medio declarado (gestionado por Bean Validation sobre campos individuales), 422
 * para contenido que el servidor entendió pero rechaza por motivos de negocio.
 */
public class InvalidProductQueryException extends InvalidQueryException {

    public InvalidProductQueryException(String message, String messageKey, Object... args) {
        super(message, messageKey, args);
    }

    /**
     * Coincide con la forma de la fábrica de excepciones de
     * {@link com.ej1.app.common.domain.SortableFieldValidator}, de modo que
     * {@code InvalidProductQueryException::new} pueda usarse allí como method reference.
     */
    public InvalidProductQueryException(String sortBy, Set<String> allowedFields) {
        super("sortBy '" + sortBy + "' is not sortable. Allowed values: " + allowedFields,
                "error.query.invalidSortField", sortBy, allowedFields);
    }
}
