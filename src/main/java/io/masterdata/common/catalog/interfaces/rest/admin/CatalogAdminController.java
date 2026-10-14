package io.masterdata.common.catalog.interfaces.rest.admin;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.service.CatalogCommandService;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.interfaces.rest.CatalogTypePathConverter;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogAdminResponse;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogRequest;
import io.masterdata.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import io.masterdata.common.rest.PageHeaders;
import io.masterdata.common.rest.SortDirection;
import io.masterdata.common.rest.patch.JsonPatchApplier;
import io.masterdata.common.rest.patch.JsonPatchOperation;
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
import java.util.Locale;

/**
 * API de <b>gestión</b> de catálogos (backoffice): CRUD completo bajo
 * {@code /admin/api/catalogs/{type}}, separado de la API de consulta
 * ({@code /api/catalogs/{type}}, ver {@code CatalogQueryController}) por prefijo de ruta
 * para que seguridad, gateway o caché puedan tratarla con una sola regla
 * ({@code /admin/**}). Toda respuesta es {@link CatalogAdminResponse}, con <b>todas</b>
 * las traducciones indexadas por locale — la misma forma que acepta {@link CatalogRequest},
 * lista para un formulario de edición. {@code Accept-Language} solo influye en el filtro y
 * el orden por {@code name}/{@code description} del listado. {@code type} se resuelve sin
 * distinguir mayúsculas/minúsculas por {@link CatalogTypePathConverter}.
 */
@RestController
@RequestMapping("/admin/api/catalogs/{type}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalogs (gestión)", description = "Gestión (CRUD) de catálogos de datos maestros (marcas, categorías, ...), con todas las traducciones")
public class CatalogAdminController {

    private final CatalogQueryService queryService;
    private final CatalogCommandService commandService;
    private final CatalogRestMapper mapper;
    private final JsonPatchApplier jsonPatchApplier;

    @GetMapping
    @Operation(summary = "Listar entradas de un catálogo (con todas sus traducciones)",
            description = "Mismo filtro y orden que la API de consulta, pero cada entrada lleva translations con todos los "
                    + "idiomas e incluye también las entradas desactivadas. A diferencia de la API de consulta, "
                    + "pagina por defecto (paged=true): el cuerpo es la lista sin envolver y los metadatos van en "
                    + "las cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages. Con paged=false "
                    + "devuelve todas las coincidencias sin esas cabeceras. Sintaxis de filter: condiciones campo:valor separadas por ; y combinadas todas con AND, p. ej. name:logi;website:acme,beta;price.gte:10. Varios valores separados por , significan «cualquiera de ellos» (texto: contiene alguno, sin distinguir mayúsculas; otros tipos: IN). Números y fechas (ISO-8601) admiten rangos con sufijo: campo.gt, campo.gte, campo.lt, campo.lte (un único valor). \\; \\, y \\\\ son ; , y \\ literales; los : dentro del valor no necesitan escaparse. name/description se evalúan sobre el texto en el idioma de Accept-Language (con fallback al idioma por defecto). Campos filtrables: name, description, enabled (true/false) y los adicionales del type (ver filterOperators en GET /admin/api/catalog-types).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente. Las cabeceras de página solo se envían con paged=true.",
                    headers = {
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Count", description = "Total de elementos que cumplen los criterios (solo con paged=true)", schema = @Schema(type = "integer", format = "int64")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Number", description = "Página devuelta, 0-based (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Size", description = "Tamaño de página aplicado (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Pages", description = "Total de páginas (solo con paged=true)", schema = @Schema(type = "integer"))
                    }),
            @ApiResponse(responseCode = "400", description = "sortDirection no es ASC ni DESC, o page/size fuera de rango", content = @Content),
            @ApiResponse(responseCode = "422", description = "sortBy no es un campo ordenable de catálogo (name, description, weight, o un campo adicional de este type), o filter está mal formado, nombra un campo no filtrable para este type, usa un operador no válido para su tipo o trae un valor que no encaja con él", content = @Content)
    })
    public ResponseEntity<List<CatalogAdminResponse>> findAll(
            @Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
            @Parameter(description = "Condiciones de filtrado campo:valor separadas por ; (AND). Ver la descripción de la operación.", example = "name:logi;enabled:false;website:.com")
            @RequestParam(required = false) String filter,
            @Parameter(description = "Campo de ordenación: name, description, weight, o un campo adicional propio de este type. 422 si no es válido para este type.")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Dirección de ordenación")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection,
            @Parameter(description = "Si es true (por defecto), pagina según page/size y añade las cabeceras X-*. Si es false, devuelve todas las coincidencias.")
            @RequestParam(defaultValue = "true") boolean paged,
            @Parameter(description = "Página solicitada (0-based). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Tamaño de página (1-100). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Locale locale) {
        var criteria = new CatalogSearchCriteria(CatalogFilter.parse(filter));
        if (!paged) {
            List<Catalog> catalogs = queryService.findAll(type, locale.getLanguage(), criteria, sortBy, sortDirection.name());
            return ResponseEntity.ok(mapper.toAdminResponseList(catalogs));
        }
        PageResult<Catalog> result = queryService.findPage(type, locale.getLanguage(), criteria,
                new Paging(page, size, sortBy, sortDirection.name()));
        return ResponseEntity.ok()
                .headers(PageHeaders.of(result))
                .body(mapper.toAdminResponseList(result.content()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una entrada de catálogo por id (con todas sus traducciones)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada encontrada"),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content)
    })
    public ResponseEntity<CatalogAdminResponse> findById(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                          @Parameter(description = "Id de la entrada") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toAdminResponse(queryService.findById(type, id)));
    }

    @PostMapping
    @Operation(summary = "Crear una entrada de catálogo",
            description = "Además de weight/translations, el payload admite cualquier campo adicional propio de "
                    + "este type (p. ej. website para brand: las columnas propias de su entidad) "
                    + "como atributo normal de primer nivel; se devuelve igual, aplanado, en la "
                    + "respuesta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrada creada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una entrada con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CatalogAdminResponse> create(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                        @Valid @RequestBody CatalogRequest request) {
        var created = commandService.create(mapper.toCreateCommand(type, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toAdminResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una entrada de catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada actualizada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe una entrada con ese nombre en algún idioma", content = @Content)
    })
    public ResponseEntity<CatalogAdminResponse> update(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                        @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                        @Valid @RequestBody CatalogRequest request) {
        return ResponseEntity.ok(mapper.toAdminResponse(commandService.update(type, id, mapper.toUpdateCommand(request))));
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
    public ResponseEntity<CatalogAdminResponse> patch(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                       @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                       @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                               description = "Documento JSON Patch (RFC 6902): array de operaciones",
                                                               required = true,
                                                               content = @Content(mediaType = "application/json-patch+json"))
                                                       @RequestBody List<JsonPatchOperation> patch) {
        Catalog existing = queryService.findById(type, id);
        CatalogRequest current = mapper.toRequest(existing);

        CatalogRequest patchedRequest = jsonPatchApplier.apply(current, patch);

        Catalog updated = commandService.update(type, id, mapper.toUpdateCommand(patchedRequest));
        return ResponseEntity.ok(mapper.toAdminResponse(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (desactivar) una entrada de catálogo",
            description = "Borrado lógico: pone enabled=false en lugar de eliminar la fila, así que los datos que la "
                    + "referencian siguen resolviéndola por id. Idempotente. Para recuperarla, PUT/PATCH con enabled=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entrada desactivada (o ya lo estaba)", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                        @Parameter(description = "Id de la entrada") @PathVariable Long id) {
        commandService.delete(type, id);
        return ResponseEntity.noContent().build();
    }
}
