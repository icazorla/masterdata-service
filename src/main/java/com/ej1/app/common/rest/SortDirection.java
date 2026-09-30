package com.ej1.app.common.rest;

/**
 * Dirección de ordenación aceptada por los endpoints de listado/búsqueda que exponen
 * {@code sortDirection} como query param (catálogos, productos, ...). Modelada como
 * enum del método del controlador — en vez de {@code String} + {@code @Pattern} — para
 * que springdoc genere el {@code enum: [ASC, DESC]} del esquema OpenAPI automáticamente
 * (un {@code @Pattern} solo aporta un {@code pattern} de texto, que Swagger UI no
 * muestra como una lista de valores). Un valor no reconocido falla directamente en el
 * binding de Spring MVC — 400, vía el {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
 * que ya gestiona {@code ApiExceptionHandler} para cualquier otro path/query param con
 * tipo propio (p. ej. {@code CatalogType}) — sin necesitar una restricción de Bean
 * Validation aparte.
 */
public enum SortDirection {
    ASC, DESC
}
