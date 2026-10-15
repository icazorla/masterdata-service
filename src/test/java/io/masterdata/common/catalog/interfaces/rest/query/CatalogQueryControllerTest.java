package io.masterdata.common.catalog.interfaces.rest.query;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.TestCriteria;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import io.masterdata.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import io.masterdata.common.catalog.interfaces.rest.CatalogTypePathConverter;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import io.masterdata.common.rest.exception.ApiExceptionHandler;
import io.masterdata.common.rest.exception.SecurityErrorHandler;
import io.masterdata.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de consulta ({@code /api/catalogs/{type}}): solo lectura y localizada. {@code type} es
 * un segmento de la ruta resuelto por {@link CatalogTypePathConverter}, así que la mayor parte
 * del comportamiento (idéntico para cada {@link CatalogType}) se ejercita una sola vez contra
 * {@code /api/catalogs/region}; {@link #getAll_routesProvinceTypeToo()} y
 * {@link #getById_returns400WhenTypeIsUnknown()} cubren específicamente el enrutado/parseo del
 * tipo, y {@link #writes_return405()} que no hay escrituras.
 *
 * <p>Con la seguridad real ({@link SecurityConfig}) y sin credenciales: la API de consulta es pública.
 */
@WebMvcTest(CatalogQueryController.class)
@Import({ApiExceptionHandler.class, CatalogRestMapper.class, CatalogTypePathConverter.class,
        CatalogI18nConfig.class,
        SecurityConfig.class, SecurityErrorHandler.class})
class CatalogQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService queryService;

    private Catalog buildEntity(Long id, String name) {
        return Catalog.builder().id(id).type(TestCatalogTypes.REGION)
                .translations(List.of(new CatalogTranslation("en", name, "desc"))).build();
    }

    private static final CatalogSearchCriteria ONLY_ENABLED = TestCriteria.of(null, null, true);

    @Test
    void getAll_returns200WithList() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_routesProvinceTypeToo() throws Exception {
        Catalog province = Catalog.builder().id(2L).type(TestCatalogTypes.PROVINCE)
                .translations(List.of(new CatalogTranslation("en", "Almería", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.PROVINCE, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(province));

        mockMvc.perform(get("/api/catalogs/province"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Almería"));
    }

    @Test
    void getAll_isCaseInsensitiveOnType() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/REGION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    @DisplayName("getAll pasa filter/sortBy/sortDirection/locale al servicio para que el filtro y el orden se resuelvan en base de datos")
    void getAll_passesFiltersSortAndLocaleToService() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "es", TestCriteria.of("logi", "periph", true), "description", "DESC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region")
                        .param("filter", "name:logi;description:periph")
                        .param("sortBy", "description").param("sortDirection", "DESC")
                        .header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    @DisplayName("getAll interpreta filter (listas por comas, rangos por sufijo) y añade siempre enabled:true al final")
    void getAll_parsesFilterAndAppendsEnabled() throws Exception {
        var criteria = new CatalogSearchCriteria(List.of(
                new CatalogFilter("name", "logi"),
                new CatalogFilter("ineCode", "1", "2"),
                new CatalogFilter("stock", CatalogFilter.Operator.GTE, List.of("5")),
                new CatalogFilter("enabled", "true")));
        when(queryService.findAll(TestCatalogTypes.REGION, "en", criteria, "name", "ASC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region").param("filter", "name:logi;ineCode:1,2;stock.gte:5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_returns422WhenFilterOperatorIsUnknown() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("filter", "stock.between:1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getAll_returns422WhenFilterIsMalformed() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("filter", "logi"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("La API de consulta no permite filtrar por enabled (siempre lista solo activas)")
    void getAll_returns422WhenFilteringByEnabled() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("filter", "enabled:false"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Los parámetros sueltos que no son de la operación se ignoran (ya no se interpretan como filtros)")
    void getAll_ignoresUnknownTopLevelParameters() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region").param("ineCode", "1").param("enabled", "false"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_allowsSortingByWeight() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "weight", "DESC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region").param("sortBy", "weight").param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    @DisplayName("La respuesta de consulta no expone type, weight ni createdAt/updatedAt (solo en la API de gestión)")
    void getAll_responseOmitsAdminOnlyFields() throws Exception {
        Catalog full = Catalog.builder().id(1L).type(TestCatalogTypes.REGION).weight(7)
                .createdAt(LocalDateTime.of(2026, 10, 1, 10, 0)).updatedAt(LocalDateTime.of(2026, 10, 1, 10, 0))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(full));

        mockMvc.perform(get("/api/catalogs/region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[0].weight").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("getAll expone los campos adicionales del tipo (ineCode) como atributo de primer nivel, no anidado")
    void getAll_responseFlattensExtraFields() throws Exception {
        Catalog withIneCode = Catalog.builder().id(1L).type(TestCatalogTypes.REGION)
                .extraFields(Map.of("ineCode", 1))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(withIneCode));

        mockMvc.perform(get("/api/catalogs/region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ineCode").value(1))
                .andExpect(jsonPath("$[0].extraFields").doesNotExist());
    }

    @Test
    @DisplayName("GET sin paged devuelve la lista completa, sin cabeceras de página ni llamada a findPage")
    void getAll_unpagedByDefault() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/region").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"))
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(header().doesNotExist("X-Total-Pages"));

        verify(queryService, never()).findPage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET con paged=true pagina con page/size/filtro/orden y devuelve las cabeceras de página")
    void getAll_pagedReturnsPageAndHeaders() throws Exception {
        var criteria = TestCriteria.of("logi", null, true);
        var paging = new Paging(1, 2, "weight", "DESC");
        when(queryService.findPage(TestCatalogTypes.REGION, "en", criteria, paging))
                .thenReturn(new PageResult<>(List.of(buildEntity(3L, "Logitech")), 1, 2, 3, 2));

        mockMvc.perform(get("/api/catalogs/region")
                        .param("paged", "true")
                        .param("page", "1")
                        .param("size", "2")
                        .param("filter", "name:logi")
                        .param("sortBy", "weight")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(header().string("X-Page-Number", "1"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(header().string("X-Total-Pages", "2"));

        verify(queryService, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET con paged=true usa page=0 y size=20 por defecto")
    void getAll_pagedUsesDefaultPageAndSize() throws Exception {
        when(queryService.findPage(TestCatalogTypes.REGION, "en", ONLY_ENABLED, new Paging(0, 20, "name", "ASC")))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/catalogs/region").param("paged", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void getAll_returns400WhenSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("paged", "true").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalogs/region").param("paged", "true").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenPageNegative() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("paged", "true").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenPagedNotBoolean() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("paged", "maybe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenSortDirectionInvalid() throws Exception {
        mockMvc.perform(get("/api/catalogs/region").param("sortDirection", "sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns422WhenSortByNotSortable() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", ONLY_ENABLED, "id", "ASC"))
                .thenThrow(new InvalidCatalogQueryException("id", java.util.Set.of("name", "description")));

        mockMvc.perform(get("/api/catalogs/region").param("sortBy", "id"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getById_returns400WhenTypeIsUnknown() throws Exception {
        mockMvc.perform(get("/api/catalogs/widget/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/region/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("GET por id devuelve también una entrada desactivada, marcada con enabled=false")
    void getById_returnsDisabledEntry() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L))
                .thenReturn(buildEntity(1L, "Logitech").toBuilder().enabled(false).build());

        mockMvc.perform(get("/api/catalogs/region/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void getById_fallsBackToDefaultLocaleWhenTranslationMissing() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/region/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L));

        mockMvc.perform(get("/api/catalogs/region/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Autonomous community not found: id=999"));
    }

    @Test
    void getById_returns404WithSpanishMessageWhenAcceptLanguageIsSpanish() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L));

        mockMvc.perform(get("/api/catalogs/region/{id}", 999L).header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comunidad autónoma: no existe ningún registro con id=999"));
    }

    @Test
    @DisplayName("La API de consulta es de solo lectura: POST/PUT/PATCH/DELETE devuelven 405 con la cabecera Allow")
    void writes_return405() throws Exception {
        mockMvc.perform(post("/api/catalogs/region").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(put("/api/catalogs/region/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/catalogs/region/{id}", 1L).contentType("application/json-patch+json").content("[]"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/catalogs/region/{id}", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("El antiguo /{id}/translations ya no existe en la API de consulta (está en GET /admin/api/catalogs/{type}/{id}): 404, no 500")
    void translations_returns404() throws Exception {
        mockMvc.perform(get("/api/catalogs/region/{id}/translations", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
