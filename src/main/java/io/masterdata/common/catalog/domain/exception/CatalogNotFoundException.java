package io.masterdata.common.catalog.domain.exception;

import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.domain.exception.NotFoundException;

/**
 * Una sola clase de excepción para todos los tipos de catálogo. Usa la clave i18n genérica {@code error.catalog.notFound} con el propio
 * {@link CatalogType} como primer argumento — que el {@code MessageSource} traduce
 * a su nombre (ver {@link CatalogType#getCodes()}) — así que un nuevo tipo obtiene
 * el 404 localizado sin definir ningún mensaje propio.
 */
public class CatalogNotFoundException extends NotFoundException {
    public CatalogNotFoundException(CatalogType type, Long id) {
        super(type.label() + " not found: id=" + id, "error.catalog.notFound", type, id);
    }
}
