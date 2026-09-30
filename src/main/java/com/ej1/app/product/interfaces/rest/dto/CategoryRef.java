package com.ej1.app.product.interfaces.rest.dto;

/**
 * Proyección ligera {@code {id, name}} de una categoría, embebida en {@link ProductResponse}.
 * Deliberadamente no es {@code com.ej1.app.common.catalog.interfaces.rest.dto.CatalogResponse}
 * (que también lleva {@code description}/timestamps) — un listado de productos solo
 * necesita lo suficiente para identificar y etiquetar la categoría, no el registro completo
 * de datos maestros.
 */
public record CategoryRef(Long id, String name) {
}
