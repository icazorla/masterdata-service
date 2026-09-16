package com.ej1.app.product.interfaces.rest;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.service.ProductService;
import com.ej1.app.product.interfaces.rest.dto.ProductQueryRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductResponse;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import com.ej1.app.shared.domain.Paging;
import com.ej1.app.shared.rest.PageHeaders;
import com.ej1.app.shared.rest.patch.JsonPatchApplier;
import com.ej1.app.shared.rest.patch.JsonPatchOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "CRUD de productos")
public class ProductController {

    private final ProductService service;
    private final ProductRestMapper mapper;
    private final JsonPatchApplier jsonPatchApplier;

    @GetMapping
    @Operation(summary = "Listar productos", description = "Devuelve los productos existentes, paginados y sin filtrar. "
            + "El cuerpo de la respuesta es la lista de productos sin envolver; los metadatos de página viajan en las "
            + "cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages — mismo formato que "
            + "POST /api/products/query.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente",
                    headers = {
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Count", description = "Total de elementos que cumplen los criterios", schema = @Schema(type = "integer", format = "int64")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Number", description = "Página devuelta (0-based)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Size", description = "Tamaño de página aplicado", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Pages", description = "Total de páginas", schema = @Schema(type = "integer"))
                    }),
            @ApiResponse(responseCode = "400", description = "page/size/sortDirection con formato inválido", content = @Content),
            @ApiResponse(responseCode = "422", description = "sortBy no es un campo ordenable de producto", content = @Content)
    })
    public ResponseEntity<List<ProductResponse>> findAll(
            @Parameter(description = "Página solicitada (0-based)")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Tamaño de página (1-100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Campo de ordenación. Valores válidos: id, name, price, stock, sku, "
                    + "categoryId, brandId, createdAt, updatedAt (se valida como regla de negocio, no de formato — 422 si no es válido).")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Dirección de ordenación")
            @RequestParam(defaultValue = "ASC")
            @Pattern(regexp = "ASC|DESC", message = "sortDirection debe ser ASC o DESC")
            String sortDirection) {
        var criteria = new ProductSearchCriteria(null, null, null, null, null, null, null, null);
        var result = service.search(criteria, new Paging(page, size, sortBy, sortDirection));
        return ResponseEntity.ok()
                .headers(PageHeaders.of(result))
                .body(mapper.toResponseList(result.content()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> findById(@Parameter(description = "Id del producto") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(id)));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Obtener producto por SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> findBySku(@Parameter(description = "SKU del producto") @PathVariable String sku) {
        return ResponseEntity.ok(mapper.toResponse(service.findBySku(sku)));
    }

    @PostMapping("/query")
    @Operation(summary = "Buscar productos (RFC 10008 — semántica HTTP QUERY sobre POST)",
            description = "Ejecuta una búsqueda de productos cuyos filtros viajan en el cuerpo de la petición "
                    + "en vez de en la URL, siguiendo la semántica del método HTTP QUERY definido en "
                    + "[RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html) (safe, idempotente, consulta con cuerpo). "
                    + "Expuesta sobre POST porque Spring Framework aún no soporta el verbo QUERY de forma nativa "
                    + "(llega en Spring Framework 7.1). Todos los filtros del cuerpo son opcionales y se combinan con AND; "
                    + "la paginación y el orden viajan como query params, igual que en el listado paginado. El cuerpo de "
                    + "la respuesta es la lista de productos sin envolver; los metadatos de página viajan en las "
                    + "cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta ejecutada correctamente",
                    headers = {
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Count", description = "Total de elementos que cumplen los criterios", schema = @Schema(type = "integer", format = "int64")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Number", description = "Página devuelta (0-based)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Size", description = "Tamaño de página aplicado", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Pages", description = "Total de páginas", schema = @Schema(type = "integer"))
                    }),
            @ApiResponse(responseCode = "400", description = "Payload o query params inválidos (tipos, tamaño de página, sortDirection distinto de ASC/DESC...)", content = @Content),
            @ApiResponse(responseCode = "415", description = "Content-Type no soportado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Consulta sintácticamente válida pero semánticamente inconsistente (p. ej. minPrice > maxPrice, o sortBy no es un campo ordenable de producto)", content = @Content)
    })
    public ResponseEntity<List<ProductResponse>> query(
            @Parameter(description = "Página solicitada (0-based)")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Tamaño de página (1-100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Campo de ordenación. Valores válidos: id, name, price, stock, sku, "
                    + "categoryId, brandId, createdAt, updatedAt (se valida como regla de negocio, no de formato — 422 si no es válido).")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Dirección de ordenación")
            @RequestParam(defaultValue = "ASC")
            @Pattern(regexp = "ASC|DESC", message = "sortDirection debe ser ASC o DESC")
            String sortDirection,
            @Valid @RequestBody ProductQueryRequest request) {
        var criteria = mapper.toCriteria(request);
        var result = service.search(criteria, new Paging(page, size, sortBy, sortDirection));
        return ResponseEntity.ok()
                .header("Accept-Query", "application/json")
                .headers(PageHeaders.of(result))
                .body(mapper.toResponseList(result.content()));
    }

    @PostMapping
    @Operation(summary = "Crear producto")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "SKU ya existente", content = @Content)
    })
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        var created = service.create(mapper.toCreateCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "SKU ya existente", content = @Content)
    })
    public ResponseEntity<ProductResponse> update(@Parameter(description = "Id del producto") @PathVariable Long id,
                                                   @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toUpdateCommand(request))));
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Actualizar parcialmente un producto (JSON Patch, RFC 6902)",
            description = "Aplica un documento JSON Patch (RFC 6902) sobre el producto existente. "
                    + "Operaciones soportadas: add, remove, replace, move, copy, test. "
                    + "Content-Type requerido: application/json-patch+json. "
                    + "Solo se pueden parchear los campos editables (los mismos que en el payload de creación/actualización); "
                    + "id/createdAt/updatedAt no forman parte del documento y cualquier operación sobre ellos falla con 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado"),
            @ApiResponse(responseCode = "400", description = "Documento JSON Patch inválido, operación no aplicable o resultado inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Operación 'test' falló, o el SKU resultante ya existe", content = @Content)
    })
    public ResponseEntity<ProductResponse> patch(@Parameter(description = "Id del producto") @PathVariable Long id,
                                                  @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                          description = "Documento JSON Patch (RFC 6902): array de operaciones",
                                                          required = true,
                                                          content = @Content(mediaType = "application/json-patch+json"))
                                                  @RequestBody List<JsonPatchOperation> patch) {
        Product existing = service.findById(id);
        ProductRequest current = mapper.toRequest(existing);

        ProductRequest patchedRequest = jsonPatchApplier.apply(current, patch);

        Product updated = service.update(id, mapper.toUpdateCommand(patchedRequest));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar producto")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Id del producto") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
