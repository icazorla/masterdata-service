package com.ej1.app.category.interfaces.rest;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.service.CategoryService;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryResponse;
import com.ej1.app.category.interfaces.rest.dto.CategoryTranslationResponse;
import com.ej1.app.category.interfaces.rest.mapper.CategoryRestMapper;
import com.ej1.app.shared.rest.patch.JsonPatchApplier;
import com.ej1.app.shared.rest.patch.JsonPatchOperation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "CRUD de categorías (datos maestros)")
public class CategoryController {

    private final CategoryService service;
    private final CategoryRestMapper mapper;
    private final JsonPatchApplier jsonPatchApplier;

    @GetMapping
    @Operation(summary = "Listar categorías", description = "Devuelve todas las categorías, ordenadas por nombre. Sin paginar: es una tabla de datos maestros. name/description se resuelven según Accept-Language, con fallback al idioma por defecto.")
    public ResponseEntity<List<CategoryResponse>> findAll(Locale locale) {
        return ResponseEntity.ok(mapper.toResponseList(service.findAll(), locale));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener categoría por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content)
    })
    public ResponseEntity<CategoryResponse> findById(@Parameter(description = "Id de la categoría") @PathVariable Long id, Locale locale) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(id), locale));
    }

    @GetMapping("/{id}/translations")
    @Operation(summary = "Obtener todas las traducciones de una categoría",
            description = "Uso administrativo: devuelve name/description en todos los idiomas a la vez (clave = locale), en vez de resolver uno solo según Accept-Language.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traducciones encontradas"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content)
    })
    public ResponseEntity<Map<String, CategoryTranslationResponse>> findTranslations(@Parameter(description = "Id de la categoría") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toTranslationResponses(service.findById(id).getTranslations()));
    }

    @PostMapping
    @Operation(summary = "Crear categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request, Locale locale) {
        var created = service.create(mapper.toCreateCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created, locale));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CategoryResponse> update(@Parameter(description = "Id de la categoría") @PathVariable Long id,
                                                    @Valid @RequestBody CategoryRequest request,
                                                    Locale locale) {
        return ResponseEntity.ok(mapper.toResponse(service.update(id, mapper.toUpdateCommand(request)), locale));
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Actualizar parcialmente una categoría (JSON Patch, RFC 6902)",
            description = "Aplica un documento JSON Patch (RFC 6902) sobre el payload de la categoría — el mismo "
                    + "documento que aceptaría un POST/PUT (translations es un objeto, clave = locale, así que se "
                    + "direcciona directamente por idioma). Añadir o reemplazar la traducción en español: "
                    + "{\"op\":\"add\",\"path\":\"/translations/es\",\"value\":{\"name\":\"...\",\"description\":\"...\"}}. "
                    + "Modificar solo la descripción en inglés: {\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"...\"}. "
                    + "Eliminar un idioma: {\"op\":\"remove\",\"path\":\"/translations/es\"} (falla con 400 si es el idioma por defecto). "
                    + "Content-Type requerido: application/json-patch+json.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
            @ApiResponse(responseCode = "400", description = "Documento JSON Patch inválido, operación no aplicable o resultado inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Operación 'test' falló, o el nombre resultante ya existe en ese idioma", content = @Content)
    })
    public ResponseEntity<CategoryResponse> patch(@Parameter(description = "Id de la categoría") @PathVariable Long id,
                                                   @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                           description = "Documento JSON Patch (RFC 6902): array de operaciones",
                                                           required = true,
                                                           content = @Content(mediaType = "application/json-patch+json"))
                                                   @RequestBody List<JsonPatchOperation> patch,
                                                   Locale locale) {
        Category existing = service.findById(id);
        CategoryRequest current = mapper.toRequest(existing);

        CategoryRequest patchedRequest = jsonPatchApplier.apply(current, patch);

        Category updated = service.update(id, mapper.toUpdateCommand(patchedRequest));
        return ResponseEntity.ok(mapper.toResponse(updated, locale));
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
