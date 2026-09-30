package com.ej1.app.common.catalog.domain;

import org.jspecify.annotations.Nullable;

/**
 * El {@code name}/{@code description} de un {@link Catalog} para un locale. Un
 * catálogo lleva uno de estos por cada idioma soportado en lugar de un único
 * par de strings fijo.
 */
public record CatalogTranslation(String locale, String name, @Nullable String description) {
}
