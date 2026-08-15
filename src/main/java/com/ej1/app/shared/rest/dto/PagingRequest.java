package com.ej1.app.shared.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination and sort requested for a search/query endpoint — grouped under a
 * single {@code page} object in the JSON body (mirrors the {@code number}/
 * {@code size} vocabulary Spring Data itself uses for pages) instead of four
 * loose top-level fields. Generic and framework-boundary-only (no dependency
 * on any aggregate), so any feature's query DTO can reuse it as its
 * {@code page} field.
 *
 * <p>{@code sortDirection} is validated here (only {@code ASC}/{@code DESC} are
 * ever meaningful, for any entity). {@code sortBy} deliberately carries no
 * {@code @Pattern} or enum constraint: the set of sortable field names is a
 * property of whichever entity owns the search, not of pagination in general,
 * so a shared class has no correct fixed list to validate against. Each
 * feature's own domain service is expected to reject an unknown {@code sortBy}
 * as a business rule (see e.g. {@code ProductService.search} — same 422
 * "content understood but invalid" treatment as any other cross-field query
 * rule), the same way {@code minPrice > maxPrice} isn't a single-field
 * {@code @Pattern} either.
 *
 * <p>All fields are deliberately {@code null}-able with no field default here:
 * Jackson deserializes the enclosing request through the Lombok-generated
 * all-args constructor when every property is present (and passes explicit
 * {@code null} for properties missing from the JSON body), which bypasses plain
 * Java field initializers entirely. Defaults belong instead in the owning
 * feature's MapStruct mapper, via {@code @Mapping(defaultValue = ...)} on the
 * {@code source = "page.number"} / {@code "page.size"} / {@code "page.sortBy"} /
 * {@code "page.sortDirection"} paths.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginación y orden solicitados. Si se omite por completo, aplica los defaults del endpoint.")
public class PagingRequest {

    @PositiveOrZero
    @Schema(description = "Página solicitada (0-based).", example = "0")
    private Integer number;

    @Min(1)
    @Max(100)
    @Schema(description = "Tamaño de página (1-100).", example = "20")
    private Integer size;

    @Schema(description = "Campo de ordenación. Los valores válidos dependen de la entidad consultada "
            + "(el endpoint documenta cuáles); un valor desconocido se rechaza como regla de negocio, no como error de formato.",
            example = "price")
    private String sortBy;

    @Pattern(regexp = "ASC|DESC", message = "sortDirection debe ser ASC o DESC")
    @Schema(description = "Dirección de ordenación.", example = "ASC")
    private String sortDirection;
}
