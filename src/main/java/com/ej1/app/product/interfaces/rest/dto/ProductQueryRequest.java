package com.ej1.app.product.interfaces.rest.dto;

import com.ej1.app.shared.rest.dto.PagingRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Query criteria for {@code POST /api/products/query} — the request body
 * carries the query instead of URL parameters, per RFC 10008 ("The HTTP QUERY
 * Method") semantics. All filters are optional and combine with AND.
 *
 * <p>Pagination and sort are grouped under a single {@code page} object (see the
 * reusable {@link PagingRequest}) instead of loose top-level fields — e.g.
 * {@code {"page": {"number": 0, "size": 20, "sortBy": "price", "sortDirection": "DESC"}, ...}}.
 * {@code sortBy}'s allowed values are Product-specific (see {@code ProductService.search}
 * for the whitelist and the 422 it raises on an unknown one) — {@link PagingRequest}
 * itself stays generic and validates only what's universal ({@code number}/{@code size}
 * bounds, {@code sortDirection} being {@code ASC}/{@code DESC}). {@code page} is
 * deliberately left {@code null}-able with no field default here: Jackson deserializes
 * this class through the Lombok-generated all-args constructor when every property is
 * present (and passes explicit {@code null} for properties missing from the JSON body),
 * which bypasses plain Java field initializers entirely. Defaults are resolved in exactly
 * one place instead — {@code ProductRestMapper.toCriteria}'s {@code @Mapping(defaultValue = ...)}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Criterios de búsqueda de productos. La consulta viaja en el cuerpo (semántica RFC 10008) en vez de en la URL.")
public class ProductQueryRequest {

    @Size(max = 120)
    @Schema(description = "Coincidencia parcial (contains, sin distinguir mayúsculas) sobre el nombre", example = "teclado")
    private String name;

    @Size(max = 40)
    @Schema(description = "Coincidencia parcial sobre el SKU", example = "KB-")
    private String sku;

    @Size(max = 20)
    @Schema(description = "Coincidencia parcial sobre la categoría. Admite varios valores: "
            + "un producto coincide si contiene alguno de ellos (OR).",
            example = "[\"Periféricos\", \"Pantallas\"]")
    private List<@Size(max = 80) String> category;

    @Size(max = 20)
    @Schema(description = "Coincidencia parcial sobre la marca. Admite varios valores: "
            + "un producto coincide si contiene alguna de ellas (OR).",
            example = "[\"Logitech\", \"Samsung\"]")
    private List<@Size(max = 80) String> brand;

    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Precio mínimo, inclusive", example = "10.00")
    private BigDecimal minPrice;

    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Precio máximo, inclusive", example = "100.00")
    private BigDecimal maxPrice;

    @Schema(description = "Filtra por estado activo/inactivo", example = "true")
    private Boolean active;

    @Size(max = 500)
    @Schema(description = "Coincidencia parcial sobre los tags", example = "rgb")
    private String tags;

    @Valid
    @Schema(description = "Paginación y orden solicitados, agrupados. Si se omite, aplica number=0, size=20, "
            + "sortBy=id, sortDirection=ASC. Valores válidos de sortBy: id, name, price, stock, sku, category, "
            + "brand, createdAt, updatedAt.")
    private PagingRequest page;
}
