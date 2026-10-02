package com.ej1.app.common.catalog.interfaces.rest.query;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogTypeResponse;
import com.ej1.app.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Metadatos de los catálogos: qué {@link CatalogType} hay configurados y qué campos tienen
 * sus entradas. Parte de la API de consulta (es de solo lectura), aunque también la usa el
 * backoffice para construir sus formularios. Separado de {@link CatalogQueryController}
 * porque este vive en la raíz {@code /api/catalogs}, sin el segmento {@code {type}} que
 * aquel lleva a nivel de clase.
 */
@RestController
@RequestMapping("/api/catalogs")
@RequiredArgsConstructor
@Tag(name = "Catalogs (consulta)", description = "Consulta de catálogos de datos maestros (marcas, categorías, ...), localizada según Accept-Language")
public class CatalogTypeController {

    private final CatalogQueryService queryService;
    private final CatalogRestMapper mapper;
    private final MessageSource messageSource;

    @GetMapping
    @Operation(summary = "Listar los tipos de catálogo y sus campos",
            description = "Devuelve todos los tipos de catálogo configurados (app.catalog.types, en el orden de "
                    + "application.yml) con su nombre traducido según Accept-Language y la definición de los "
                    + "campos de sus entradas: primero los comunes a todo catálogo (id, name, description, weight, "
                    + "createdAt, updatedAt) y después los adicionales propios del tipo (p. ej. website para brand). "
                    + "Pensado para que un cliente construya formularios/listados genéricos sin conocer los tipos "
                    + "de antemano.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    public ResponseEntity<List<CatalogTypeResponse>> findAll(Locale locale) {
        return ResponseEntity.ok(queryService.findTypeDefinitions().stream()
                .map(definition -> mapper.toTypeResponse(definition, messageSource.getMessage(definition.type(), locale)))
                .toList());
    }
}
