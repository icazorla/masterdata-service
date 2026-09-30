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
 * Criterios de consulta para {@code POST /api/products/query} — el cuerpo de la
 * petición transporta los filtros en lugar de parámetros de URL, siguiendo la
 * semántica de la RFC 10008 ("The HTTP QUERY Method"). Todos los filtros son
 * opcionales y se combinan con AND.
 *
 * <p>La paginación y el orden ({@code page}/{@code size}/{@code sortBy}/
 * {@code sortDirection}) deliberadamente <em>no</em> forman parte de este cuerpo:
 * viajan como parámetros de consulta normales, igual que en
 * {@code GET /api/products} — ver {@code ProductController.query}. Eso mantiene
 * "qué buscar" (cuerpo) y "cómo paginar/ordenar el resultado" (parámetros de
 * consulta) en el canal de transporte al que cada uno pertenece por convención.
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
