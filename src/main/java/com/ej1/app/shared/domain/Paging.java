package com.ej1.app.shared.domain;

/**
 * Pagination and sort applied to a domain-level search: {@code page}/
 * {@code size}/{@code sortBy}/{@code sortDirection} grouped under a single value
 * object instead of four loose fields repeated on every {@code *SearchCriteria}
 * record, so any feature's search criteria can reuse it instead of duplicating
 * the group.
 *
 * <p>Lives in {@code shared.domain}, not {@code shared.rest}: it's plain Java
 * with zero framework dependency (no Spring, no Jackson, no Bean Validation),
 * which is what lets the {@code domain} layer depend on it without breaking the
 * "domain knows nothing about frameworks" rule — {@code shared.rest} (Spring
 * MVC/Jackson/Bean Validation-bound) stays off-limits to {@code domain} for
 * that same reason.
 *
 * <p>Every field here is always present: by the time a {@code *SearchCriteria}
 * reaches the domain, the controller has already resolved defaults for
 * {@code page}/{@code size}/{@code sortBy}/{@code sortDirection} from the request's
 * query params (see e.g. {@code ProductController.findAll}/{@code query}, which
 * declare {@code @RequestParam(defaultValue = ...)} for each).
 */
public record Paging(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
}
