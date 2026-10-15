package io.masterdata.common.catalog.interfaces.rest.query;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.interfaces.rest.CatalogApiDocs;
import io.masterdata.common.catalog.interfaces.rest.CatalogTypePathConverter;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogResponse;
import io.masterdata.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import io.masterdata.common.rest.PageHeaders;
import io.masterdata.common.rest.SortDirection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * API de <b>consulta</b> de catálogos: solo lectura, pensada para los clientes finales
 * (front, otros servicios). Cada entrada se devuelve con un único {@code name}/
 * {@code description} resuelto según {@code Accept-Language}. Solo depende de
 * {@link CatalogQueryService}, así que no puede modificar nada; las escrituras viven en
 * la API de gestión ({@code /admin/api/catalogs/{type}}, ver {@code CatalogAdminController}).
 * El listado solo devuelve entradas activas ({@code enabled}); la consulta por id devuelve
 * también las desactivadas, porque otros datos pueden seguir referenciándolas.
 * {@code type} es un segmento de la ruta, p. ej. {@code /api/catalogs/province}, resuelto sin
 * distinguir mayúsculas/minúsculas por {@link CatalogTypePathConverter}.
 */
@RestController
@RequestMapping("/api/catalogs/{type}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalogs (consulta)", description = "Consulta de catálogos de datos maestros (comunidades autónomas, provincias, ...), localizada según Accept-Language")
public class CatalogQueryController {

    private final CatalogQueryService queryService;
    private final CatalogRestMapper mapper;

    @GetMapping
    @Operation(summary = "Listar entradas de un catálogo",
            description = "Devuelve las entradas activas (enabled=true) del catálogo indicado que cumplen filter, ordenadas por "
                    + "nombre, descripción, peso o un campo adicional del type (weight siempre como primer "
                    + "criterio). Por defecto sin paginar (paged=false): devuelve todas las coincidencias, pensado "
                    + "para poblar filtros/selects. Con paged=true devuelve solo la página page (0-based) de tamaño "
                    + "size; el cuerpo sigue siendo la lista sin envolver y los metadatos de página viajan en las "
                    + "cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages. El orden y el "
                    + "name/description de la respuesta se resuelven sobre la traducción de Accept-Language, con "
                    + "fallback al idioma por defecto. " + CatalogApiDocs.FILTER_SYNTAX + " y los adicionales del type (ver filterOperators en GET /admin/api/catalogs); enabled no se puede filtrar aquí.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente. Las cabeceras de página solo se envían con paged=true.",
                    headers = {
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Count", description = "Total de elementos que cumplen los criterios (solo con paged=true)", schema = @Schema(type = "integer", format = "int64")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Number", description = "Página devuelta, 0-based (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Size", description = "Tamaño de página aplicado (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Pages", description = "Total de páginas (solo con paged=true)", schema = @Schema(type = "integer"))
                    }),
            @ApiResponse(responseCode = "400", description = "sortDirection no es ASC ni DESC, o page/size fuera de rango (se validan aunque paged=false)", content = @Content),
            @ApiResponse(responseCode = "422", description = CatalogApiDocs.INVALID_QUERY, content = @Content)
    })
    public ResponseEntity<List<CatalogResponse>> findAll(
            @Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
            @Parameter(description = "Condiciones de filtrado campo:valor separadas por ; (AND). Ver la descripción de la operación.", example = "name:sev;regionCode:1")
            @RequestParam(required = false) String filter,
            @Parameter(description = "Campo de ordenación. Siempre válidos: name, description, weight; además, cualquier campo adicional propio de este type (p. ej. regionCode para province). Se valida como regla de negocio, no de formato — 422 si no es válido para este type.")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Dirección de ordenación")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection,
            @Parameter(description = "Si es true, pagina el resultado según page/size y añade las cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages. Si es false (por defecto), devuelve todas las coincidencias.")
            @RequestParam(defaultValue = "false") boolean paged,
            @Parameter(description = "Página solicitada (0-based). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Tamaño de página (1-100). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Locale locale) {
        var criteria = new CatalogSearchCriteria(CatalogFilter.parse(filter));
        if (criteria.filtersBy(CatalogFilter.ENABLED)) {
            throw InvalidCatalogQueryException.filterNotAllowed(CatalogFilter.ENABLED);
        }
        criteria = criteria.and(new CatalogFilter(CatalogFilter.ENABLED, "true"));
        if (!paged) {
            List<Catalog> catalogs = queryService.findAll(type, locale.getLanguage(), criteria, sortBy, sortDirection.name());
            return ResponseEntity.ok(mapper.toResponseList(catalogs, locale));
        }
        PageResult<Catalog> result = queryService.findPage(type, locale.getLanguage(), criteria,
                new Paging(page, size, sortBy, sortDirection.name()));
        return ResponseEntity.ok()
                .headers(PageHeaders.of(result))
                .body(mapper.toResponseList(result.content(), locale));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una entrada de catálogo por id",
            description = "Devuelve la entrada aunque esté desactivada (enabled=false), para poder mostrar datos que aún la referencian.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada encontrada"),
            @ApiResponse(responseCode = "404", description = "Entrada no encontrada", content = @Content)
    })
    public ResponseEntity<CatalogResponse> findById(@Parameter(description = "Tipo de catálogo") @PathVariable CatalogType type,
                                                     @Parameter(description = "Id de la entrada") @PathVariable Long id,
                                                     Locale locale) {
        return ResponseEntity.ok(mapper.toResponse(queryService.findById(type, id), locale));
    }
}
