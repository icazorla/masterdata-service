package io.masterdata.common.catalog.interfaces.rest.admin;

import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogTypeResponse;
import io.masterdata.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
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
 * sus entradas. Parte de la API de gestión (rol {@code ADMIN}, regla {@code /admin/**}):
 * aunque es de solo lectura, describe el modelo completo —{@code weight}, {@code enabled},
 * {@code createdAt}/{@code updatedAt}, qué es traducible u obligatorio— para que el backoffice
 * construya sus formularios, algo que los clientes de la API de consulta no necesitan.
 * Separado de {@link CatalogAdminController} porque este vive en la raíz
 * {@code /admin/api/catalogs}, sin el segmento {@code {type}} que aquel lleva a nivel de clase.
 */
@RestController
@RequestMapping("/admin/api/catalogs")
@RequiredArgsConstructor
@Tag(name = "Catalogs (gestión)", description = "Gestión (CRUD) de catálogos de datos maestros (comunidades autónomas, provincias, ...), con todas las traducciones")
public class CatalogTypeController {

    private final CatalogQueryService queryService;
    private final CatalogRestMapper mapper;
    private final MessageSource messageSource;

    @GetMapping
    @Operation(summary = "Listar los tipos de catálogo y sus campos",
            description = "Devuelve todos los tipos de catálogo configurados (app.catalog.types, en el orden de "
                    + "application.yml) con su nombre traducido según Accept-Language y la definición de los "
                    + "campos de sus entradas: primero los comunes a todo catálogo (id, name, description, weight, "
                    + "createdAt, updatedAt) y después los adicionales propios del tipo (p. ej. regionCode para province). "
                    + "Pensado para que el backoffice construya formularios/listados genéricos sin conocer los tipos "
                    + "de antemano.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    public ResponseEntity<List<CatalogTypeResponse>> findAll(Locale locale) {
        return ResponseEntity.ok(queryService.findTypeDefinitions().stream()
                .map(definition -> mapper.toTypeResponse(definition, messageSource.getMessage(definition.type(), locale)))
                .toList());
    }
}
