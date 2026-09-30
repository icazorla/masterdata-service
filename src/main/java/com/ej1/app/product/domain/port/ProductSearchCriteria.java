package com.ej1.app.product.domain.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * Criterios de búsqueda para consultar productos — la idea de "HTTP QUERY" de la
 * RFC 10008 aplicada a nivel de dominio: la consulta viaja como un value object
 * autocontenido en lugar de una lista dispersa de parámetros de URL. Forma parte
 * del puerto de salida del dominio — independiente de cualquier DTO de transporte.
 *
 * <p>{@code name}/{@code sku}/{@code tags} coinciden sin distinguir mayúsculas como
 * subcadena; {@code minPrice}/{@code maxPrice}/{@code active} coinciden de forma exacta.
 * {@code categoryId}/{@code brandId} admiten varios valores — un producto coincide
 * cuando el id de su categoría/marca es <em>cualquiera</em> de los indicados (unidos
 * con OR), y ese grupo se combina luego con AND junto con los demás filtros, igual
 * que lo haría un único valor.
 *
 * <p>Deliberadamente no lleva paginación/orden: <em>qué</em> coincide (este record)
 * y <em>cómo paginar/ordenar el resultado</em> ({@code com.ej1.app.common.domain.Paging})
 * son responsabilidades independientes, que se pasan como parámetros separados allí
 * donde se necesitan ambas (ver {@code ProductRepository#search}/{@code ProductService#search}) —
 * un objeto, una fuente de verdad para cada una.
 */
public record ProductSearchCriteria(
        String name,
        String sku,
        List<Long> categoryId,
        List<Long> brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean active,
        String tags
) {
}
