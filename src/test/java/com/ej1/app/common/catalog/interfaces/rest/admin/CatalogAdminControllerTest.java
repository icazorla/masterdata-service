package com.ej1.app.common.catalog.interfaces.rest.admin;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import com.ej1.app.common.catalog.domain.service.CatalogCommandService;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogPersistenceConfig;
import com.ej1.app.common.catalog.infrastructure.rest.CatalogRestMapperAdapter;
import com.ej1.app.common.catalog.interfaces.rest.CatalogTypePathConverter;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogRequest;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogTranslationRequest;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import com.ej1.app.common.rest.exception.ApiExceptionHandler;
import com.ej1.app.common.rest.exception.JsonPatchExceptionHandler;
import com.ej1.app.common.rest.patch.JsonPatchApplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
 * API de gestión ({@code /admin/api/catalogs/{type}}): CRUD completo, respuestas con todas
 * las traducciones ({@code CatalogAdminResponse}). El comportamiento es idéntico para cada
 * {@link CatalogType}, así que se ejercita contra {@code /admin/api/catalogs/brand}; el
 * enrutado/parseo del tipo y la parte localizada de las lecturas se cubren en
 * {@code CatalogQueryControllerTest}.
 */
@WebMvcTest(CatalogAdminController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, CatalogRestMapperAdapter.class,
        JsonPatchApplier.class, CatalogTypePathConverter.class, CatalogPersistenceConfig.class, CatalogI18nConfig.class})
class CatalogAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CatalogQueryService queryService;

    @MockitoBean
    private CatalogCommandService commandService;

    private Catalog buildEntity(Long id, String name) {
        return Catalog.builder().id(id).type(TestCatalogTypes.BRAND)
                .translations(List.of(new CatalogTranslation("en", name, "desc"))).build();
    }

    private Catalog buildBilingualEntity() {
        return Catalog.builder().id(1L).type(TestCatalogTypes.BRAND).weight(3)
                .extraFields(Map.of("website", "https://www.logitech.com"))
                .translations(List.of(
                        new CatalogTranslation("en", "Logitech", "Peripheral manufacturer"),
                        new CatalogTranslation("es", "Logitech", "Fabricante de periféricos")))
                .build();
    }

    private CatalogRequest buildRequest(String name) {
        return CatalogRequest.builder()
                .translations(Map.of("en", CatalogTranslationRequest.builder().name(name).description("desc").build()))
                .build();
    }

    private static final CatalogSearchCriteria NO_FILTER = new CatalogSearchCriteria(null, null);

    @Test
    @DisplayName("GET pagina por defecto (paged=true) y devuelve todas las traducciones de cada entrada, no una sola resuelta")
    void getAll_pagedByDefaultWithAllTranslations() throws Exception {
        when(queryService.findPage(TestCatalogTypes.BRAND, "en", NO_FILTER, new Paging(0, 20, "name", "ASC")))
                .thenReturn(new PageResult<>(List.of(buildBilingualEntity()), 0, 20, 1, 1));

        mockMvc.perform(get("/admin/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("brand"))
                .andExpect(jsonPath("$[0].weight").value(3))
                .andExpect(jsonPath("$[0].website").value("https://www.logitech.com"))
                .andExpect(jsonPath("$[0].extraFields").doesNotExist())
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].translations.en.description").value("Peripheral manufacturer"))
                .andExpect(jsonPath("$[0].translations.es.description").value("Fabricante de periféricos"))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(header().string("X-Total-Pages", "1"));

        verify(queryService, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET pasa filtros, orden, paginación y el idioma de Accept-Language (para filtrar/ordenar por name) al servicio")
    void getAll_passesFiltersSortPagingAndLocaleToService() throws Exception {
        var criteria = new CatalogSearchCriteria("logi", "periph");
        var paging = new Paging(1, 2, "weight", "DESC");
        when(queryService.findPage(TestCatalogTypes.BRAND, "es", criteria, paging))
                .thenReturn(new PageResult<>(List.of(buildBilingualEntity()), 1, 2, 3, 2));

        mockMvc.perform(get("/admin/api/catalogs/brand")
                        .param("name", "logi").param("description", "periph")
                        .param("sortBy", "weight").param("sortDirection", "DESC")
                        .param("page", "1").param("size", "2")
                        .header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(header().string("X-Page-Number", "1"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(header().string("X-Total-Pages", "2"));
    }

    @Test
    @DisplayName("GET con paged=false devuelve la lista completa sin cabeceras de página")
    void getAll_unpagedWhenRequested() throws Exception {
        when(queryService.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildBilingualEntity()));

        mockMvc.perform(get("/admin/api/catalogs/brand").param("paged", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].translations.en.name").value("Logitech"))
                .andExpect(header().doesNotExist("X-Total-Count"));

        verify(queryService, never()).findPage(any(), any(), any(), any());
    }

    @Test
    void getAll_returns400WhenSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs/brand").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns422WhenSortByNotSortable() throws Exception {
        when(queryService.findPage(TestCatalogTypes.BRAND, "en", NO_FILTER, new Paging(0, 20, "id", "ASC")))
                .thenThrow(new InvalidCatalogQueryException("id", java.util.Set.of("name", "description")));

        mockMvc.perform(get("/admin/api/catalogs/brand").param("sortBy", "id"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /{id} devuelve todas las traducciones indexadas por locale, ignorando Accept-Language")
    void getById_returnsEveryLocaleKeyed() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildBilingualEntity());

        mockMvc.perform(get("/admin/api/catalogs/brand/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.name").value("Logitech"))
                .andExpect(jsonPath("$.translations.es.description").value("Fabricante de periféricos"))
                .andExpect(jsonPath("$.website").value("https://www.logitech.com"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L));

        mockMvc.perform(get("/admin/api/catalogs/brand/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Brand not found: id=999"));
    }

    @Test
    void getById_returns400WhenTypeIsUnknown() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs/widget/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create devuelve la entrada creada con todas sus traducciones")
    void create_returnsAllTranslations() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildBilingualEntity());

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.es.name").value("Logitech"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/admin/api/catalogs/brand/1"))
                .andExpect(jsonPath("$.id").value(1));

        verify(commandService).create(argThat(cmd -> cmd.type().equals(TestCatalogTypes.BRAND)));
    }

    @Test
    void create_propagatesWeightFromRequest() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(7)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> cmd.weight() == 7));
    }

    @Test
    @DisplayName("create captura un atributo de primer nivel no declarado (website) en extraFields, vía @JsonAnySetter")
    void create_propagatesExtraFieldFromRequest() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"website\":\"https://www.logitech.com\","
                                + "\"translations\":{\"en\":{\"name\":\"Logitech\",\"description\":\"desc\"}}}"))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> "https://www.logitech.com".equals(cmd.extraFields().get("website"))));
    }

    @Test
    void create_defaultsWeightToZeroWhenOmitted() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> cmd.weight() == 0));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        CatalogRequest invalid = CatalogRequest.builder().translations(Map.of()).build();

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenDefaultLocaleMissing() throws Exception {
        CatalogRequest invalid = CatalogRequest.builder()
                .translations(Map.of("es", CatalogTranslationRequest.builder().name("Logitech").build()))
                .build();

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenLocaleNotSupported() throws Exception {
        CatalogRequest invalid = CatalogRequest.builder()
                .translations(Map.of(
                        "en", CatalogTranslationRequest.builder().name("Logitech").build(),
                        "fr", CatalogTranslationRequest.builder().name("Logitech").build()))
                .build();

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(TestCatalogTypes.BRAND, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech Pro"));

        mockMvc.perform(put("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.name").value("Logitech Pro"));
    }

    @Test
    void update_propagatesWeightFromRequest() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(9)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(put("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.BRAND), eq(1L), argThat(cmd -> cmd.weight() == 9));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(999L), any(UpdateCatalogCommand.class)))
                .thenThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L));

        mockMvc.perform(put("/admin/api/catalogs/brand/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_replacesTranslationFieldByLocale() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"New description\"}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.BRAND), eq(1L), argThat(cmd -> cmd.translations().stream()
                .anyMatch(t -> t.locale().equals("en") && "New description".equals(t.description()))));
    }

    @Test
    void patch_replacesWeight() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/weight\",\"value\":15}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.BRAND), eq(1L), argThat(cmd -> cmd.weight() == 15));
    }

    @Test
    @DisplayName("patch reemplaza website (campo extra de brand) por /website: JsonPatchApplier serializa/deserializa "
            + "CatalogRequest a través de @JsonAnyGetter/@JsonAnySetter, así que el path es plano, no /extraFields/website")
    void patch_replacesExtraField() throws Exception {
        Catalog existingWithWebsite = Catalog.builder().id(1L).type(TestCatalogTypes.BRAND)
                .extraFields(Map.of("website", "https://old.example.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(existingWithWebsite);
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/website\",\"value\":\"https://new.example.com\"}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.BRAND), eq(1L),
                argThat(cmd -> "https://new.example.com".equals(cmd.extraFields().get("website"))));
    }

    @Test
    void patch_addsNewLocaleByKey() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"add\",\"path\":\"/translations/es\","
                                + "\"value\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.BRAND), eq(1L), argThat(cmd -> cmd.translations().size() == 2
                && cmd.translations().stream().anyMatch(t -> t.locale().equals("es"))));
    }

    @Test
    void patch_returns400WhenRemovingDefaultLocale() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"remove\",\"path\":\"/translations/en\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns409WhenNameDuplicated() throws Exception {
        when(queryService.findById(TestCatalogTypes.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.BRAND), eq(1L), any(UpdateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(TestCatalogTypes.BRAND, "Samsung"));

        mockMvc.perform(patch("/admin/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"Samsung\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(commandService).delete(TestCatalogTypes.BRAND, 1L);

        mockMvc.perform(delete("/admin/api/catalogs/brand/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new CatalogNotFoundException(TestCatalogTypes.BRAND, 999L)).when(commandService).delete(TestCatalogTypes.BRAND, 999L);

        mockMvc.perform(delete("/admin/api/catalogs/brand/{id}", 999L))
                .andExpect(status().isNotFound());
    }
}
