package io.masterdata.common.domain;

/**
 * Paginación y ordenación aplicadas a una búsqueda a nivel de dominio. Java puro, sin
 * dependencias de framework, para que la capa {@code domain} pueda usarlo.
 *
 * <p>Todos los campos están siempre presentes: el controlador ya ha resuelto sus valores
 * por defecto a partir de los query params ({@code @RequestParam(defaultValue = ...)}).
 */
public record Paging(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
}
