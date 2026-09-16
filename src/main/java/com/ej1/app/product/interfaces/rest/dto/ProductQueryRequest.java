package com.ej1.app.product.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Query criteria for {@code POST /api/products/query} — the request body
 * carries the filters instead of URL parameters, per RFC 10008 ("The HTTP
 * QUERY Method") semantics. All filters are optional and combine with AND.
 *
 * <p>Pagination and sort ({@code page}/{@code size}/{@code sortBy}/
 * {@code sortDirection}) are deliberately <em>not</em> part of this body: they
 * travel as regular query parameters instead, the same way they do on
 * {@code GET /api/products} — see {@code ProductController.query}. That keeps
 * "what to search for" (body) and "how to page/sort the result" (query params)
 * on the transport channel each one conventionally belongs to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Criterios de búsqueda de productos. La consulta viaja en el cuerpo (semántica RFC 10008) en vez de en la URL.")
public class ProductQueryRequest {

    @Size(max = 120)
    @Schema(description = "Coincidencia parcial (contains, sin distinguir mayúsculas) sobre el nombre", example = "teclado")
    private @Nullable String name;

    @Size(max = 40)
    @Schema(description = "Coincidencia parcial sobre el SKU", example = "KB-")
    private @Nullable String sku;

    @Size(max = 20)
    @Schema(description = "Filtra por id de categoría. Admite varios valores: "
            + "un producto coincide si su categoría es alguno de ellos (OR).",
            example = "[1, 2]")
    private @Nullable List<Long> categoryId;

    @Size(max = 20)
    @Schema(description = "Filtra por id de marca. Admite varios valores: "
            + "un producto coincide si su marca es alguna de ellas (OR).",
            example = "[3, 4]")
    private @Nullable List<Long> brandId;

    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Precio mínimo, inclusive", example = "10.00")
    private @Nullable BigDecimal minPrice;

    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Precio máximo, inclusive", example = "100.00")
    private @Nullable BigDecimal maxPrice;

    @Schema(description = "Filtra por estado activo/inactivo", example = "true")
    private @Nullable Boolean active;

    @Size(max = 500)
    @Schema(description = "Coincidencia parcial sobre los tags", example = "rgb")
    private @Nullable String tags;
}
