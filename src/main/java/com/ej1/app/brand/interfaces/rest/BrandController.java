package com.ej1.app.brand.interfaces.rest;

import com.ej1.app.brand.domain.service.BrandService;
import com.ej1.app.brand.interfaces.rest.dto.BrandRequest;
import com.ej1.app.brand.interfaces.rest.dto.BrandResponse;
import com.ej1.app.brand.interfaces.rest.mapper.BrandRestMapper;
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
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Tag(name = "Brands", description = "CRUD de marcas (datos maestros)")
public class BrandController {

    private final BrandService service;
    private final BrandRestMapper mapper;

    @GetMapping
    @Operation(summary = "Listar marcas", description = "Devuelve todas las marcas, ordenadas por nombre. Sin paginar: es una tabla de datos maestros.")
    public ResponseEntity<List<BrandResponse>> findAll() {
        return ResponseEntity.ok(mapper.toResponseList(service.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener marca por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marca encontrada"),
            @ApiResponse(responseCode = "404", description = "Marca no encontrada", content = @Content)
    })
    public ResponseEntity<BrandResponse> findById(@Parameter(description = "Id de la marca") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(id)));
    }

    @PostMapping
    @Operation(summary = "Crear marca")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Marca creada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una marca con ese nombre", content = @Content)
    })
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        var created = service.create(mapper.toCreateCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar marca")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marca actualizada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Marca no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una marca con ese nombre", content = @Content)
    })
    public ResponseEntity<BrandResponse> update(@Parameter(description = "Id de la marca") @PathVariable Long id,
                                                 @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toUpdateCommand(request))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar marca")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marca eliminada", content = @Content(schema = @io.swagger.v3.oas.annotations.media.Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Marca no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La marca está en uso por algún producto", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Id de la marca") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
