package com.ej1.app.shared.domain;

import java.util.List;

/**
 * A page of {@code T} results — {@code content}/{@code page}/{@code size}/
 * {@code totalElements}/{@code totalPages} grouped under a single parameterized
 * record instead of a dedicated {@code *Page} record per feature (formerly
 * {@code ProductPage}). Any {@code *Repository#search} that returns a page of
 * its own aggregate reuses this instead of repeating the same five fields
 * under a new name. Every paginated listing endpoint projects this straight
 * onto its HTTP response — the result list plus
 * {@link com.ej1.app.shared.rest.PageHeaders} — with no separate wrapper DTO.
 *
 * <p>Deliberately framework-agnostic — the domain must not depend on
 * {@code org.springframework.data.domain.Page}. Named {@code PageResult}, not
 * {@code Page}, on purpose: a distinct name keeps that boundary visible at a
 * glance in an import list, rather than relying only on the package to tell
 * this apart from Spring Data's own type of the same short name. Infrastructure
 * adapters map their own paging types (e.g. Spring Data's {@code Page<Entity>})
 * onto this record.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
