package com.ej1.app.category.interfaces.rest;

import com.ej1.app.category.domain.service.CategoryService;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryResponse;
import com.ej1.app.category.interfaces.rest.mapper.CategoryRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "CRUD de categorías (datos maestros)")
public class CategoryController {

    private final CategoryService service;
    private final CategoryRestMapper mapper;

    @GetMapping
    @Operation(summary = "Listar categorías", description = "Devuelve todas las categorías, ordenadas por nombre. Sin paginar: es una tabla de datos maestros.")
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(mapper.toResponseList(service.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener categoría por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content)
    })
    public ResponseEntity<CategoryResponse> findById(@Parameter(description = "Id de la categoría") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre", content = @Content)
    })
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        var created = service.create(mapper.toCreateCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre", content = @Content)
    })
    public ResponseEntity<CategoryResponse> update(@Parameter(description = "Id de la categoría") @PathVariable Long id,
                                                     @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toUpdateCommand(request))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoría eliminada", content = @Content(schema = @io.swagger.v3.oas.annotations.media.Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La categoría está en uso por algún producto", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Id de la categoría") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
