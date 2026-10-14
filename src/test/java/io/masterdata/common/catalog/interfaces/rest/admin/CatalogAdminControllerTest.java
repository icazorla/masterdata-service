package io.masterdata.common.catalog.interfaces.rest.admin;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.TestCriteria;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.exception.DuplicateCatalogNameException;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;
import io.masterdata.common.catalog.domain.service.CatalogCommandService;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import io.masterdata.common.catalog.infrastructure.rest.CatalogRestMapperAdapter;
import io.masterdata.common.catalog.interfaces.rest.CatalogTypePathConverter;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogRequest;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogTranslationRequest;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import io.masterdata.common.rest.exception.ApiExceptionHandler;
import io.masterdata.common.rest.exception.JsonPatchExceptionHandler;
import io.masterdata.common.rest.exception.SecurityErrorHandler;
import io.masterdata.common.rest.patch.JsonPatchApplier;
import io.masterdata.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
 * {@link CatalogType}, así que se ejercita contra {@code /admin/api/catalogs/region}; el
 * enrutado/parseo del tipo y la parte localizada de las lecturas se cubren en
 * {@code CatalogQueryControllerTest}.
 *
 * <p>Con la seguridad real ({@link SecurityConfig}): todos los tests se ejecutan como un usuario
 * con rol {@code ADMIN} salvo los de seguridad ({@code security_*}), que lo sobrescriben.
 */
@WebMvcTest(CatalogAdminController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, CatalogRestMapperAdapter.class,
        JsonPatchApplier.class, CatalogTypePathConverter.class, CatalogI18nConfig.class,
        SecurityConfig.class, SecurityErrorHandler.class})
@WithMockUser(roles = SecurityConfig.ADMIN)
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
        return Catalog.builder().id(id).type(TestCatalogTypes.REGION)
                .translations(List.of(new CatalogTranslation("en", name, "desc"))).build();
    }

    private Catalog buildBilingualEntity() {
        return Catalog.builder().id(1L).type(TestCatalogTypes.REGION).weight(3)
                .extraFields(Map.of("ineCode", 1))
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

    private static final CatalogSearchCriteria NO_FILTER = TestCriteria.of(null, null, null);

    @Test
    @DisplayName("GET pagina por defecto (paged=true) y devuelve todas las traducciones de cada entrada, no una sola resuelta")
    void getAll_pagedByDefaultWithAllTranslations() throws Exception {
        when(queryService.findPage(TestCatalogTypes.REGION, "en", NO_FILTER, new Paging(0, 20, "name", "ASC")))
                .thenReturn(new PageResult<>(List.of(buildBilingualEntity()), 0, 20, 1, 1));

        mockMvc.perform(get("/admin/api/catalogs/region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("region"))
                .andExpect(jsonPath("$[0].weight").value(3))
                .andExpect(jsonPath("$[0].ineCode").value(1))
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
        var criteria = TestCriteria.of("logi", "periph", null);
        var paging = new Paging(1, 2, "weight", "DESC");
        when(queryService.findPage(TestCatalogTypes.REGION, "es", criteria, paging))
                .thenReturn(new PageResult<>(List.of(buildBilingualEntity()), 1, 2, 3, 2));

        mockMvc.perform(get("/admin/api/catalogs/region")
                        .param("filter", "name:logi;description:periph")
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
    @DisplayName("GET pasa el filtro enabled al servicio; sin él lista activas y desactivadas")
    void getAll_passesEnabledFilterToService() throws Exception {
        var paging = new Paging(0, 20, "name", "ASC");
        when(queryService.findPage(TestCatalogTypes.REGION, "en", TestCriteria.of(null, null, false), paging))
                .thenReturn(new PageResult<>(List.of(buildEntity(1L, "Logitech").toBuilder().enabled(false).build()), 0, 20, 1, 1));

        mockMvc.perform(get("/admin/api/catalogs/region").param("filter", "enabled:false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(false));
    }

    @Test
    @DisplayName("GET con paged=false devuelve la lista completa sin cabeceras de página")
    void getAll_unpagedWhenRequested() throws Exception {
        when(queryService.findAll(TestCatalogTypes.REGION, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildBilingualEntity()));

        mockMvc.perform(get("/admin/api/catalogs/region").param("paged", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].translations.en.name").value("Logitech"))
                .andExpect(header().doesNotExist("X-Total-Count"));

        verify(queryService, never()).findPage(any(), any(), any(), any());
    }

    @Test
    void getAll_returns400WhenSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs/region").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns422WhenSortByNotSortable() throws Exception {
        when(queryService.findPage(TestCatalogTypes.REGION, "en", NO_FILTER, new Paging(0, 20, "id", "ASC")))
                .thenThrow(new InvalidCatalogQueryException("id", java.util.Set.of("name", "description")));

        mockMvc.perform(get("/admin/api/catalogs/region").param("sortBy", "id"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /{id} devuelve todas las traducciones indexadas por locale, ignorando Accept-Language")
    void getById_returnsEveryLocaleKeyed() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildBilingualEntity());

        mockMvc.perform(get("/admin/api/catalogs/region/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.name").value("Logitech"))
                .andExpect(jsonPath("$.translations.es.description").value("Fabricante de periféricos"))
                .andExpect(jsonPath("$.ineCode").value(1));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L));

        mockMvc.perform(get("/admin/api/catalogs/region/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Autonomous community not found: id=999"));
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

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.es.name").value("Logitech"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/admin/api/catalogs/region/1"))
                .andExpect(jsonPath("$.id").value(1));

        verify(commandService).create(argThat(cmd -> cmd.type().equals(TestCatalogTypes.REGION)));
    }

    @Test
    void create_propagatesWeightFromRequest() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(7)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> cmd.weight() == 7));
    }

    @Test
    @DisplayName("create captura un atributo de primer nivel no declarado (ineCode) en extraFields, vía @JsonAnySetter")
    void create_propagatesExtraFieldFromRequest() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ineCode\":1,"
                                + "\"translations\":{\"en\":{\"name\":\"Logitech\",\"description\":\"desc\"}}}"))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> Integer.valueOf(1).equals(cmd.extraFields().get("ineCode"))));
    }

    @Test
    void create_defaultsWeightToZeroWhenOmitted() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> cmd.weight() == 0));
    }

    @Test
    @DisplayName("create deja enabled=true cuando el payload no lo trae")
    void create_defaultsEnabledToTrueWhenOmitted() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"translations\":{\"en\":{\"name\":\"Logitech\"}}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true));

        verify(commandService).create(argThat(CreateCatalogCommand::enabled));
    }

    @Test
    void create_propagatesEnabledFromRequest() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"translations\":{\"en\":{\"name\":\"Logitech\"}}}"))
                .andExpect(status().isCreated());

        verify(commandService).create(argThat(cmd -> !cmd.enabled()));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        CatalogRequest invalid = CatalogRequest.builder().translations(Map.of()).build();

        mockMvc.perform(post("/admin/api/catalogs/region")
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

        mockMvc.perform(post("/admin/api/catalogs/region")
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

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(commandService.create(any(CreateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(TestCatalogTypes.REGION, "Logitech"));

        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech Pro"));

        mockMvc.perform(put("/admin/api/catalogs/region/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.name").value("Logitech Pro"));
    }

    @Test
    void update_propagatesWeightFromRequest() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(9)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(put("/admin/api/catalogs/region/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> cmd.weight() == 9));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(999L), any(UpdateCatalogCommand.class)))
                .thenThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L));

        mockMvc.perform(put("/admin/api/catalogs/region/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_replacesTranslationFieldByLocale() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"New description\"}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> cmd.translations().stream()
                .anyMatch(t -> t.locale().equals("en") && "New description".equals(t.description()))));
    }

    @Test
    void patch_replacesWeight() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/weight\",\"value\":15}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> cmd.weight() == 15));
    }

    @Test
    void patch_disablesEntry() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/enabled\",\"value\":false}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> !cmd.enabled()));
    }

    @Test
    @DisplayName("patch de otro campo conserva enabled=false: el documento parcheado parte del estado actual, no de los valores por defecto")
    void patch_keepsEntryDisabledWhenPatchingOtherField() throws Exception {
        Catalog disabled = buildEntity(1L, "Logitech").toBuilder().enabled(false).build();
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(disabled);
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(disabled);

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/weight\",\"value\":15}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> !cmd.enabled() && cmd.weight() == 15));
    }

    @Test
    @DisplayName("patch reemplaza ineCode (campo extra de region) por /ineCode: JsonPatchApplier serializa/deserializa "
            + "CatalogRequest a través de @JsonAnyGetter/@JsonAnySetter, así que el path es plano, no /extraFields/ineCode")
    void patch_replacesExtraField() throws Exception {
        Catalog existingWithIneCode = Catalog.builder().id(1L).type(TestCatalogTypes.REGION)
                .extraFields(Map.of("ineCode", 1))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(existingWithIneCode);
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/ineCode\",\"value\":2}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L),
                argThat(cmd -> Integer.valueOf(2).equals(cmd.extraFields().get("ineCode"))));
    }

    @Test
    void patch_addsNewLocaleByKey() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"add\",\"path\":\"/translations/es\","
                                + "\"value\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}]"))
                .andExpect(status().isOk());

        verify(commandService).update(eq(TestCatalogTypes.REGION), eq(1L), argThat(cmd -> cmd.translations().size() == 2
                && cmd.translations().stream().anyMatch(t -> t.locale().equals("es"))));
    }

    @Test
    void patch_returns400WhenRemovingDefaultLocale() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"remove\",\"path\":\"/translations/en\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 999L)).thenThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns409WhenNameDuplicated() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(commandService.update(eq(TestCatalogTypes.REGION), eq(1L), any(UpdateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(TestCatalogTypes.REGION, "Samsung"));

        mockMvc.perform(patch("/admin/api/catalogs/region/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"Samsung\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(commandService).delete(TestCatalogTypes.REGION, 1L);

        mockMvc.perform(delete("/admin/api/catalogs/region/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new CatalogNotFoundException(TestCatalogTypes.REGION, 999L)).when(commandService).delete(TestCatalogTypes.REGION, 999L);

        mockMvc.perform(delete("/admin/api/catalogs/region/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("sin credenciales: 401 con WWW-Authenticate: Basic y el cuerpo de error común, sin llegar al controller")
    void security_returns401WithoutCredentials() throws Exception {
        mockMvc.perform(post("/admin/api/catalogs/region")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"app\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required: missing or invalid credentials"));

        verifyNoInteractions(commandService);
    }

    @Test
    @WithAnonymousUser
    void security_returns401LocalizedFromAcceptLanguage() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs/region/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Se requiere autenticación: credenciales ausentes o no válidas"));
    }

    @Test
    @WithAnonymousUser
    void security_returns401WithWrongPassword() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs/region/{id}", 1L).with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("autenticado sin rol ADMIN: 403")
    void security_returns403WithoutAdminRole() throws Exception {
        mockMvc.perform(delete("/admin/api/catalogs/region/{id}", 1L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));

        verifyNoInteractions(commandService);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("HTTP Basic con el usuario de app.security.users (admin/admin en desarrollo): 200")
    void security_acceptsConfiguredUserViaHttpBasic() throws Exception {
        when(queryService.findById(TestCatalogTypes.REGION, 1L)).thenReturn(buildBilingualEntity());

        mockMvc.perform(get("/admin/api/catalogs/region/{id}", 1L).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
