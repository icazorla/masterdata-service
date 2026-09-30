package com.ej1.app.common.catalog.domain.exception;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.domain.exception.NotFoundException;

/**
 * Una sola clase de excepción para todos los tipos de catálogo en lugar de una
 * por agregado ({@code BrandNotFoundException}, {@code CategoryNotFoundException},
 * ...): {@code type} determina tanto el mensaje orientado al desarrollador
 * como la clave de mensaje i18n ({@code error.<type.code()>.notFound}), así que
 * un nuevo valor de {@link CatalogType} obtiene el manejo del 404 gratis
 * mientras existan sus entradas en el bundle de mensajes.
 */
public class CatalogNotFoundException extends NotFoundException {
    public CatalogNotFoundException(CatalogType type, Long id) {
        super(type.label() + " not found: id=" + id, "error." + type.code() + ".notFound", id);
    }
}
