package io.masterdata.common.domain;

/**
 * Paginación y ordenación aplicadas a una búsqueda a nivel de dominio: {@code page}/
 * {@code size}/{@code sortBy}/{@code sortDirection} agrupados en un único value
 * object en lugar de cuatro campos sueltos repetidos en cada record
 * {@code *SearchCriteria}, de forma que los criterios de búsqueda de cualquier
 * feature puedan reutilizarlo en lugar de duplicar el grupo.
 *
 * <p>Vive en {@code shared.domain}, no en {@code shared.rest}: es Java puro
 * sin ninguna dependencia de framework (sin Spring, sin Jackson, sin Bean Validation),
 * que es justo lo que permite que la capa {@code domain} dependa de él sin romper la
 * regla de que "el dominio no sabe nada de frameworks" — {@code shared.rest} (ligado
 * a Spring MVC/Jackson/Bean Validation) sigue estando fuera del alcance de {@code domain}
 * por esa misma razón.
 *
 * <p>Todos los campos aquí están siempre presentes: para cuando un {@code *SearchCriteria}
 * llega al dominio, el controlador ya ha resuelto los valores por defecto de
 * {@code page}/{@code size}/{@code sortBy}/{@code sortDirection} a partir de los
 * query params de la petición (ver p. ej. {@code ProductController.findAll}/{@code query},
 * que declaran {@code @RequestParam(defaultValue = ...)} para cada uno).
 */
public record Paging(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
}
