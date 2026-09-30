package com.ej1.app.common.catalog.interfaces.rest;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.service.CatalogService;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogRequest;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogResponse;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogTranslationResponse;
import com.ej1.app.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import com.ej1.app.common.rest.SortDirection;
import com.ej1.app.common.rest.patch.JsonPatchApplier;
import com.ej1.app.common.rest.patch.JsonPatchOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CRUD para todo catálogo de nombre+descripción (marca, categoría, ...), unificado
 * tras un único controlador en vez de un {@code XxxController} por cada agregado —
 * {@code type} (ver {@link CatalogType}) es un segmento de la ruta, p. ej.
 * {@code /api/catalogs/brand}, {@code /api/catalogs/category}, resuelto sin distinguir
 * mayúsculas/minúsculas por {@link CatalogTypePathConverter}.
 */
@RestController
@RequestMapping("/api/catalogs/{type}")
@RequiredArgsConstructor
@Tag(name = "Catalogs", description = "CRUD genérico de catálogos de datos maestros (marcas, categorías, ...)")
public class CatalogController {

    private final CatalogService service;
    private final CatalogRestMapper mapper;
    private final JsonPatchApplier jsonPatchApplier;

    @GetMapping
    @Operation(summary = "Listar entradas de un catálogo",
            description = "Devuelve las entradas del catálogo indicado que cumplen los filtros dados, ordenadas por "
                    + "nombre, descripción o peso. Sin paginar: son tablas de datos maestros. El filtro (subcadena, "
                    + "sin distinguir mayúsculas) solo aplica a name/description; el orden y el name/description de "
                    + "la respuesta se resuelven en base de datos sobre la misma traducción: la de Accept-Language, "
                    + "con fallback al idioma por defecto cuando esta entrada no tiene traducción propia para ese "
                    + "locale. weight no se traduce — es el mismo entero para cualquier idioma. Si se dan name y "
                    + "description a la vez, se combinan con OR (coincide si el nombre o la descripción contienen "
                    + "el filtro), no con AND.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente"),
            @ApiResponse(responseCode = "400", description = "sortDirection no es ASC ni DESC", content = @Content),
            @ApiResponse(responseCode = "422", description = "sortBy no es un campo ordenable de catálogo (name, description, weight, o un campo adicional de este type)", content = @Content)
    })
    public ResponseEntity<List<CatalogResponse>> findAll(
            @Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
            @Parameter(description = "Filtra por nombre (subcadena, sin distinguir mayúsculas), en el idioma resuelto para este cliente. Combinado con description mediante OR.")
            @RequestParam(required = false) String name,
            @Parameter(description = "Filtra por descripción (subcadena, sin distinguir mayúsculas), en el idioma resuelto para este cliente. Combinado con name mediante OR.")
            @RequestParam(required = false) String description,
            @Parameter(description = "Campo de ordenación. Siempre válidos: name, description, weight; además, cualquier campo adicional propio de este type (p. ej. website para brand — ver app.catalog.types.<type>.extra-fields). Se valida como regla de negocio, no de formato — 422 si no es válido para este type.")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Dirección de ordenación")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection,
            Locale locale) {
        var criteria = new CatalogSearchCriteria(name, description);
        List<Catalog> catalogs = service.findAll(type, locale.getLanguage(), criteria, sortBy, sortDirection.name());
        return ResponseEntity.ok(mapper.toResponseList(catalogs, locale));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una entrada de catálogo por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada encontrada"),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content)
    })
    public ResponseEntity<CatalogResponse> findById(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                     @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                     Locale locale) {
        return ResponseEntity.ok(mapper.toResponse(service.findById(type, id), locale));
    }

    @GetMapping("/{id}/translations")
    @Operation(summary = "Obtener todas las traducciones de una entrada de catálogo",
            description = "Uso administrativo: devuelve name/description en todos los idiomas a la vez (clave = locale), en vez de resolver uno solo según Accept-Language.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traducciones encontradas"),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content)
    })
    public ResponseEntity<Map<String, CatalogTranslationResponse>> findTranslations(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                                                     @Parameter(description = "Id de la entrada") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toTranslationResponses(service.findById(type, id).getTranslations()));
    }

    @PostMapping
    @Operation(summary = "Crear una entrada de catálogo",
            description = "Además de weight/translations, el payload admite cualquier campo adicional propio de "
                    + "este type (p. ej. website para brand — ver app.catalog.types.<type>.extra-fields en "
                    + "application.yml) como atributo normal de primer nivel; se devuelve igual, aplanado, en la "
                    + "respuesta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrada creada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una entrada con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CatalogResponse> create(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                   @Valid @RequestBody CatalogRequest request,
                                                   Locale locale) {
        var created = service.create(mapper.toCreateCommand(type, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created, locale));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una entrada de catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada actualizada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una entrada con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CatalogResponse> update(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                   @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                   @Valid @RequestBody CatalogRequest request,
                                                   Locale locale) {
        return ResponseEntity.ok(mapper.toResponse(service.update(type, id, mapper.toUpdateCommand(request)), locale));
    }

    @PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
    @Operation(summary = "Actualizar parcialmente una entrada de catálogo (JSON Patch, RFC 6902)",
            description = "Aplica un documento JSON Patch (RFC 6902) sobre el payload de la entrada — el mismo "
                    + "documento que aceptaría un POST/PUT (translations es un objeto, clave = locale, así que se "
                    + "direcciona directamente por idioma). Añadir o reemplazar la traducción en español: "
                    + "{\"op\":\"add\",\"path\":\"/translations/es\",\"value\":{\"name\":\"...\",\"description\":\"...\"}}. "
                    + "Modificar solo la descripción en inglés: {\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"...\"}. "
                    + "Eliminar un idioma: {\"op\":\"remove\",\"path\":\"/translations/es\"} (falla con 400 si es el idioma por defecto). "
                    + "Content-Type requerido: application/json-patch+json.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada actualizada"),
            @ApiResponse(responseCode = "400", description = "Documento JSON Patch inválido, operación no aplicable o resultado inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Operación 'test' falló, o el nombre resultante ya existe en ese idioma", content = @Content)
    })
    public ResponseEntity<CatalogResponse> patch(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                  @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                  @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                          description = "Documento JSON Patch (RFC 6902): array de operaciones",
                                                          required = true,
                                                          content = @Content(mediaType = "application/json-patch+json"))
                                                  @RequestBody List<JsonPatchOperation> patch,
                                                  Locale locale) {
        Catalog existing = service.findById(type, id);
        CatalogRequest current = mapper.toRequest(existing);

        CatalogRequest patchedRequest = jsonPatchApplier.apply(current, patch);

        Catalog updated = service.update(type, id, mapper.toUpdateCommand(patchedRequest));
        return ResponseEntity.ok(mapper.toResponse(updated, locale));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una entrada de catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entrada eliminada", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La entrada está en uso por algún producto", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                        @Parameter(description = "Id de la entrada") @PathVariable Long id) {
        service.delete(type, id);
        return ResponseEntity.noContent().build();
    }
}
