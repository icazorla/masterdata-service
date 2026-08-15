package com.ej1.app.product.domain.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * Search criteria for querying products — the RFC 10008 "HTTP QUERY" idea
 * applied at the domain level: the query travels as a self-contained value
 * object instead of a scattered list of URL parameters. Part of the domain's
 * outgoing port — independent of any transport DTO.
 *
 * <p>{@code name}/{@code sku}/{@code tags} match case-insensitively as a
 * substring; {@code minPrice}/{@code maxPrice}/{@code active} match exactly.
 * {@code category}/{@code brand} accept several values — a product matches
 * when it case-insensitively contains <em>any</em> of the given values (OR'd
 * together), and that group is then AND'd with the other filters, same as a
 * single value would be. {@code page}/{@code size}/{@code sortBy}/
 * {@code sortDirection} are always present — defaults are resolved by the
 * REST layer before the domain ever sees this record.
 */
public record ProductSearchCriteria(
        String name,
        String sku,
        List<String> category,
        List<String> brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean active,
        String tags,
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
}
