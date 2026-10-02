package com.ej1.app.common.catalog.interfaces.rest.query;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.interfaces.rest.CatalogTypePathConverter;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogResponse;
import com.ej1.app.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import com.ej1.app.common.rest.PageHeaders;
import com.ej1.app.common.rest.SortDirection;
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
 * {@code type} es un segmento de la ruta, p. ej. {@code /api/catalogs/brand}, resuelto sin
 * distinguir mayúsculas/minúsculas por {@link CatalogTypePathConverter}.
 */
@RestController
@RequestMapping("/api/catalogs/{type}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalogs (consulta)", description = "Consulta de catálogos de datos maestros (marcas, categorías, ...), localizada según Accept-Language")
public class CatalogQueryController {

    private final CatalogQueryService queryService;
    private final CatalogRestMapper mapper;

    @GetMapping
    @Operation(summary = "Listar entradas de un catálogo",
            description = "Devuelve las entradas del catálogo indicado que cumplen los filtros dados, ordenadas por "
                    + "nombre, descripción o peso. Por defecto sin paginar (paged=false): devuelve todas las "
                    + "coincidencias, pensado para poblar filtros/selects. Con paged=true devuelve solo la página "
                    + "page (0-based) de tamaño size; el cuerpo sigue siendo la lista sin envolver y los metadatos "
                    + "de página viajan en las cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages "
                    + "(sin paged, page/size se ignoran y no se envían esas cabeceras). El filtro (subcadena, "
                    + "sin distinguir mayúsculas) solo aplica a name/description; el orden y el name/description de "
                    + "la respuesta se resuelven en base de datos sobre la misma traducción: la de Accept-Language, "
                    + "con fallback al idioma por defecto cuando esta entrada no tiene traducción propia para ese "
                    + "locale. weight no se traduce — es el mismo entero para cualquier idioma. Si se dan name y "
                    + "description a la vez, se combinan con OR (coincide si el nombre o la descripción contienen "
                    + "el filtro), no con AND.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente. Las cabeceras de página solo se envían con paged=true.",
                    headers = {
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Count", description = "Total de elementos que cumplen los criterios (solo con paged=true)", schema = @Schema(type = "integer", format = "int64")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Number", description = "Página devuelta, 0-based (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Page-Size", description = "Tamaño de página aplicado (solo con paged=true)", schema = @Schema(type = "integer")),
                            @io.swagger.v3.oas.annotations.headers.Header(name = "X-Total-Pages", description = "Total de páginas (solo con paged=true)", schema = @Schema(type = "integer"))
                    }),
            @ApiResponse(responseCode = "400", description = "sortDirection no es ASC ni DESC, o page/size fuera de rango (se validan aunque paged=false)", content = @Content),
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
            @Parameter(description = "Si es true, pagina el resultado según page/size y añade las cabeceras X-Total-Count, X-Page-Number, X-Page-Size y X-Total-Pages. Si es false (por defecto), devuelve todas las coincidencias.")
            @RequestParam(defaultValue = "false") boolean paged,
            @Parameter(description = "Página solicitada (0-based). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Tamaño de página (1-100). Solo se usa con paged=true.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Locale locale) {
        var criteria = new CatalogSearchCriteria(name, description);
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
    @Operation(summary = "Obtener una entrada de catálogo por id")
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
