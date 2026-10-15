package io.masterdata.common.rest;

import io.masterdata.common.domain.PageResult;
import org.springframework.http.HttpHeaders;

import java.util.function.Consumer;

/**
 * Construye las cabeceras de respuesta {@code X-Total-Count}/{@code X-Page-Number}/{@code X-Page-Size}/{@code X-Total-Pages}
 * para un endpoint de listado paginado que devuelve un cuerpo {@code List<T>} desnudo, a
 * partir de {@link PageResult}.
 *
 * <p>Devuelve un {@link Consumer}&lt;{@link HttpHeaders}&gt; para que encaje directamente en
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
