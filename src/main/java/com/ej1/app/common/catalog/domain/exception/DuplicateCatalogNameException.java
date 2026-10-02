package com.ej1.app.common.catalog.domain.exception;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.domain.exception.DuplicateResourceException;

/**
 * Una sola clase de excepción para todos los tipos de catálogo en lugar de una
 * por agregado ({@code DuplicateBrandNameException},
 * {@code DuplicateCategoryNameException}, ...) — ver {@link CatalogNotFoundException}
 * para el porqué.
 */
public class DuplicateCatalogNameException extends DuplicateResourceException {
    public DuplicateCatalogNameException(CatalogType type, String name) {
        super(type.label() + " with name '" + name + "' already exists", "error.catalog.duplicateName", type, name);
    }
}
