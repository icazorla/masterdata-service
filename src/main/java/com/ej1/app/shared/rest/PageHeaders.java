package com.ej1.app.shared.rest;

import com.ej1.app.shared.domain.PageResult;
import org.springframework.http.HttpHeaders;

import java.util.function.Consumer;

/**
 * Builds the {@code X-Total-Count}/{@code X-Page-Number}/{@code X-Page-Size}/{@code X-Total-Pages}
 * response headers for a paginated listing endpoint that returns a bare {@code List<T>} body —
 * e.g. {@code ProductController.findAll}/{@code query}. Reusable across features: works off
 * {@link PageResult}, the generic domain-level page result, with no dependency on any aggregate.
 *
 * <p>Returns a {@link Consumer}&lt;{@link HttpHeaders}&gt; so it plugs straight into
 * {@code ResponseEntity.BodyBuilder#headers(Consumer)}:
 *
 * <pre>{@code
 * return ResponseEntity.ok()
 *         .headers(PageHeaders.of(result))
 *         .body(mapper.toResponseList(result.content()));
 * }</pre>
 */
public final class PageHeaders {

    private PageHeaders() {
    }

    public static Consumer<HttpHeaders> of(PageResult<?> page) {
        return headers -> {
            headers.add("X-Total-Count", String.valueOf(page.totalElements()));
            headers.add("X-Page-Number", String.valueOf(page.page()));
            headers.add("X-Page-Size", String.valueOf(page.size()));
            headers.add("X-Total-Pages", String.valueOf(page.totalPages()));
        };
    }
}
