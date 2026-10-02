package com.ej1.app.common.catalog.interfaces.rest.query;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogPersistenceConfig;
import com.ej1.app.common.catalog.infrastructure.rest.CatalogRestMapperAdapter;
import com.ej1.app.common.catalog.interfaces.rest.CatalogTypePathConverter;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import com.ej1.app.common.rest.exception.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * {@code /api/catalogs/brand}; {@link #getAll_routesCategoryTypeToo()} y
 * {@link #getById_returns400WhenTypeIsUnknown()} cubren específicamente el enrutado/parseo del
 * tipo, y {@link #writes_return405()} que no hay escrituras.
 */
@WebMvcTest(CatalogQueryController.class)
@Import({ApiExceptionHandler.class, CatalogRestMapperAdapter.class, CatalogTypePathConverter.class,
        CatalogPersistenceConfig.class, CatalogI18nConfig.class})
class CatalogQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService queryService;

    private Catalog buildEntity(Long id, String name) {
        return Catalog.builder().id(id).type(TestCatalogTypes.BRAND)
                .translations(List.of(new CatalogTranslation("en", name, "desc"))).build();
    }

    private static final CatalogSearchCriteria NO_FILTER = new CatalogSearchCriteria(null, null);

    @Test
    void getAll_returns200WithList() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("brand"))
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_routesCategoryTypeToo() throws Exception {
        Catalog category = Catalog.builder().id(2L).type(TestCatalogTypes.CATEGORY)
                .translations(List.of(new CatalogTranslation("en", "Peripherals", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.CATEGORY, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(category));

        mockMvc.perform(get("/api/catalogs/category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("category"))
                .andExpect(jsonPath("$[0].name").value("Peripherals"));
    }

    @Test
    void getAll_isCaseInsensitiveOnType() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/BRAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    @DisplayName("getAll pasa name/description/sortBy/sortDirection/locale al servicio para que el filtro y el orden se resuelvan en base de datos")
    void getAll_passesFiltersSortAndLocaleToService() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "es", new CatalogSearchCriteria("logi", "periph"), "description", "DESC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand")
                        .param("name", "logi").param("description", "periph")
                        .param("sortBy", "description").param("sortDirection", "DESC")
                        .header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_allowsSortingByWeight() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "weight", "DESC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand").param("sortBy", "weight").param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_responseIncludesWeight() throws Exception {
        Catalog withWeight = Catalog.builder().id(1L).type(TestCatalogTypes.BRAND).weight(7)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(withWeight));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight").value(7));
    }

    @Test
    @DisplayName("getAll expone los campos adicionales del tipo (website) como atributo de primer nivel, no anidado")
    void getAll_responseFlattensExtraFields() throws Exception {
        Catalog withWebsite = Catalog.builder().id(1L).type(TestCatalogTypes.BRAND)
                .extraFields(Map.of("website", "https://www.logitech.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(withWebsite));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].website").value("https://www.logitech.com"))
                .andExpect(jsonPath("$[0].extraFields").doesNotExist());
    }

    @Test
    @DisplayName("GET sin paged devuelve la lista completa, sin cabeceras de página ni llamada a findPage")
    void getAll_unpagedByDefault() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"))
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(header().doesNotExist("X-Total-Pages"));

        verify(queryService, never()).findPage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET con paged=true pagina con page/size/filtro/orden y devuelve las cabeceras de página")
    void getAll_pagedReturnsPageAndHeaders() throws Exception {
        var criteria = new CatalogSearchCriteria("logi", null);
        var paging = new Paging(1, 2, "weight", "DESC");
        when(queryService.findPage(TestCatalogTypes.BRAND, "en", criteria, paging))
                .thenReturn(new PageResult<>(List.of(buildEntity(3L, "Logitech")), 1, 2, 3, 2));

        mockMvc.perform(get("/api/catalogs/brand")
                        .param("paged", "true")
                        .param("page", "1")
                        .param("size", "2")
                        .param("name", "logi")
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
        when(queryService.findPage(TestCatalogTypes.BRAND, "en", NO_FILTER, new Paging(0, 20, "name", "ASC")))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/catalogs/brand").param("paged", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void getAll_returns400WhenSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand").param("paged", "true").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalogs/brand").param("paged", "true").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenPageNegative() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand").param("paged", "true").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenPagedNotBoolean() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand").param("paged", "maybe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns400WhenSortDirectionInvalid() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand").param("sortDirection", "sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns422WhenSortByNotSortable() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "id", "ASC"))
                .thenThrow(new InvalidCatalogQueryException("id", java.util.Set.of("name", "description")));

        mockMvc.perform(get("/api/catalogs/brand").param("sortBy", "id"))
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
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_fallsBackToDefaultLocaleWhenTranslationMissing() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Brand not found: id=999"));
    }

    @Test
    void getById_returns404WithSpanishMessageWhenAcceptLanguageIsSpanish() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 999L).header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Marca: no existe ningún registro con id=999"));
    }

    @Test
    @DisplayName("La API de consulta es de solo lectura: POST/PUT/PATCH/DELETE devuelven 405 con la cabecera Allow")
    void writes_return405() throws Exception {
        mockMvc.perform(post("/api/catalogs/brand").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(put("/api/catalogs/brand/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L).contentType("application/json-patch+json").content("[]"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/catalogs/brand/{id}", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("El antiguo /{id}/translations ya no existe en la API de consulta (está en GET /admin/api/catalogs/{type}/{id}): 404, no 500")
    void translations_returns404() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand/{id}/translations", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
