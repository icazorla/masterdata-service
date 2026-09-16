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
 * single value would be.
 *
 * <p>Deliberately carries no paging/sort: <em>what</em> matches (this record)
 * and <em>how to page/order the result</em> ({@code com.ej1.app.shared.domain.Paging})
 * are independent concerns, passed as separate parameters wherever both are
 * needed (see {@code ProductRepository#search}/{@code ProductService#search}) —
 * one object, one field of truth for each.
 */
public record ProductSearchCriteria(
        String name,
        String sku,
        List<String> category,
        List<String> brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean active,
        String tags
) {
}
