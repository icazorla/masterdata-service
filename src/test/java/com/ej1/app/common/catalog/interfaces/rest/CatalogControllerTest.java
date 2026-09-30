package com.ej1.app.common.catalog.interfaces.rest;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import com.ej1.app.common.catalog.domain.service.CatalogService;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogPersistenceConfig;
import com.ej1.app.common.catalog.infrastructure.rest.CatalogRestMapperAdapter;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogRequest;
import com.ej1.app.common.catalog.interfaces.rest.dto.CatalogTranslationRequest;
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
 * {@code type} es un segmento de la ruta resuelto por {@link CatalogTypePathConverter},
 * así que la mayor parte del comportamiento (idéntico para cada {@link CatalogType} —
 * ver {@link CatalogService}) se ejercita una sola vez contra {@code /api/catalogs/brand};
 * {@link #getAll_routesCategoryTypeToo()} y {@link #getById_returns400WhenTypeIsUnknown()}
 * cubren específicamente el aspecto de enrutado/parseo del tipo.
 */
@WebMvcTest(CatalogController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, CatalogRestMapperAdapter.class,
        JsonPatchApplier.class, CatalogTypePathConverter.class, CatalogPersistenceConfig.class})
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CatalogService service;

    private Catalog buildEntity(Long id, String name) {
        return Catalog.builder().id(id).type(CatalogType.BRAND)
                .translations(List.of(new CatalogTranslation("en", name, "desc"))).build();
    }

    private CatalogRequest buildRequest(String name) {
        return CatalogRequest.builder()
                .translations(Map.of("en", CatalogTranslationRequest.builder().name(name).description("desc").build()))
                .build();
    }

    private static final CatalogSearchCriteria NO_FILTER = new CatalogSearchCriteria(null, null);

    @Test
    void getAll_returns200WithList() throws Exception {
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("brand"))
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_routesCategoryTypeToo() throws Exception {
        Catalog category = Catalog.builder().id(2L).type(CatalogType.CATEGORY)
                .translations(List.of(new CatalogTranslation("en", "Peripherals", "desc"))).build();
        when(service.findAll(CatalogType.CATEGORY, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(category));

        mockMvc.perform(get("/api/catalogs/category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("category"))
                .andExpect(jsonPath("$[0].name").value("Peripherals"));
    }

    @Test
    void getAll_isCaseInsensitiveOnType() throws Exception {
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/BRAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    @DisplayName("getAll pasa name/description/sortBy/sortDirection/locale al servicio para que el filtro y el orden se resuelvan en base de datos")
    void getAll_passesFiltersSortAndLocaleToService() throws Exception {
        when(service.findAll(CatalogType.BRAND, "es", new CatalogSearchCriteria("logi", "periph"), "description", "DESC"))
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
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "weight", "DESC"))
                .thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/catalogs/brand").param("sortBy", "weight").param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getAll_responseIncludesWeight() throws Exception {
        Catalog withWeight = Catalog.builder().id(1L).type(CatalogType.BRAND).weight(7)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(withWeight));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight").value(7));
    }

    @Test
    @DisplayName("getAll expone los campos adicionales del tipo (website) como atributo de primer nivel, no anidado")
    void getAll_responseFlattensExtraFields() throws Exception {
        Catalog withWebsite = Catalog.builder().id(1L).type(CatalogType.BRAND)
                .extraFields(Map.of("website", "https://www.logitech.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "name", "ASC")).thenReturn(List.of(withWebsite));

        mockMvc.perform(get("/api/catalogs/brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].website").value("https://www.logitech.com"))
                .andExpect(jsonPath("$[0].extraFields").doesNotExist());
    }

    @Test
    void getAll_returns400WhenSortDirectionInvalid() throws Exception {
        mockMvc.perform(get("/api/catalogs/brand").param("sortDirection", "sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returns422WhenSortByNotSortable() throws Exception {
        when(service.findAll(CatalogType.BRAND, "en", NO_FILTER, "id", "ASC"))
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
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_fallsBackToDefaultLocaleWhenTranslationMissing() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.findById(CatalogType.BRAND, 999L)).thenThrow(new CatalogNotFoundException(CatalogType.BRAND, 999L));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Brand not found: id=999"));
    }

    @Test
    void getById_returns404WithSpanishMessageWhenAcceptLanguageIsSpanish() throws Exception {
        when(service.findById(CatalogType.BRAND, 999L)).thenThrow(new CatalogNotFoundException(CatalogType.BRAND, 999L));

        mockMvc.perform(get("/api/catalogs/brand/{id}", 999L).header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Marca no encontrada: id=999"));
    }

    @Test
    void getTranslations_returns200WithEveryLocaleKeyed() throws Exception {
        Catalog brand = Catalog.builder().id(1L).type(CatalogType.BRAND)
                .translations(List.of(
                        new CatalogTranslation("en", "Logitech", "Peripheral manufacturer"),
                        new CatalogTranslation("es", "Logitech", "Fabricante de periféricos")))
                .build();
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(brand);

        mockMvc.perform(get("/api/catalogs/brand/{id}/translations", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en.name").value("Logitech"))
                .andExpect(jsonPath("$.es.description").value("Fabricante de periféricos"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/catalogs/brand/1"))
                .andExpect(jsonPath("$.id").value(1));

        verify(service).create(argThat(cmd -> cmd.type().equals(CatalogType.BRAND)));
    }

    @Test
    void create_propagatesWeightFromRequest() throws Exception {
        when(service.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(7)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(service).create(argThat(cmd -> cmd.weight() == 7));
    }

    @Test
    @DisplayName("create captura un atributo de primer nivel no declarado (website) en extraFields, vía @JsonAnySetter")
    void create_propagatesExtraFieldFromRequest() throws Exception {
        when(service.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"website\":\"https://www.logitech.com\","
                                + "\"translations\":{\"en\":{\"name\":\"Logitech\",\"description\":\"desc\"}}}"))
                .andExpect(status().isCreated());

        verify(service).create(argThat(cmd -> "https://www.logitech.com".equals(cmd.extraFields().get("website"))));
    }

    @Test
    void create_defaultsWeightToZeroWhenOmitted() throws Exception {
        when(service.create(any(CreateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated());

        verify(service).create(argThat(cmd -> cmd.weight() == 0));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        CatalogRequest invalid = CatalogRequest.builder().translations(Map.of()).build();

        mockMvc.perform(post("/api/catalogs/brand")
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

        mockMvc.perform(post("/api/catalogs/brand")
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

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(service.create(any(CreateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(CatalogType.BRAND, "Logitech"));

        mockMvc.perform(post("/api/catalogs/brand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech Pro"));

        mockMvc.perform(put("/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech Pro"));
    }

    @Test
    void update_propagatesWeightFromRequest() throws Exception {
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));
        CatalogRequest request = CatalogRequest.builder().weight(9)
                .translations(Map.of("en", CatalogTranslationRequest.builder().name("Logitech").description("desc").build()))
                .build();

        mockMvc.perform(put("/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(service).update(eq(CatalogType.BRAND), eq(1L), argThat(cmd -> cmd.weight() == 9));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(service.update(eq(CatalogType.BRAND), eq(999L), any(UpdateCatalogCommand.class)))
                .thenThrow(new CatalogNotFoundException(CatalogType.BRAND, 999L));

        mockMvc.perform(put("/api/catalogs/brand/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_replacesTranslationFieldByLocale() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"New description\"}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(CatalogType.BRAND), eq(1L), argThat(cmd -> cmd.translations().stream()
                .anyMatch(t -> t.locale().equals("en") && "New description".equals(t.description()))));
    }

    @Test
    void patch_replacesWeight() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/weight\",\"value\":15}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(CatalogType.BRAND), eq(1L), argThat(cmd -> cmd.weight() == 15));
    }

    @Test
    @DisplayName("patch reemplaza website (campo extra de brand) por /website: JsonPatchApplier serializa/deserializa "
            + "CatalogRequest a través de @JsonAnyGetter/@JsonAnySetter, así que el path es plano, no /extraFields/website")
    void patch_replacesExtraField() throws Exception {
        Catalog existingWithWebsite = Catalog.builder().id(1L).type(CatalogType.BRAND)
                .extraFields(Map.of("website", "https://old.example.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(existingWithWebsite);
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/website\",\"value\":\"https://new.example.com\"}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(CatalogType.BRAND), eq(1L),
                argThat(cmd -> "https://new.example.com".equals(cmd.extraFields().get("website"))));
    }

    @Test
    void patch_addsNewLocaleByKey() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"add\",\"path\":\"/translations/es\","
                                + "\"value\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(CatalogType.BRAND), eq(1L), argThat(cmd -> cmd.translations().size() == 2
                && cmd.translations().stream().anyMatch(t -> t.locale().equals("es"))));
    }

    @Test
    void patch_returns400WhenRemovingDefaultLocale() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"remove\",\"path\":\"/translations/en\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(service.findById(CatalogType.BRAND, 999L)).thenThrow(new CatalogNotFoundException(CatalogType.BRAND, 999L));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns409WhenNameDuplicated() throws Exception {
        when(service.findById(CatalogType.BRAND, 1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(CatalogType.BRAND), eq(1L), any(UpdateCatalogCommand.class)))
                .thenThrow(new DuplicateCatalogNameException(CatalogType.BRAND, "Samsung"));

        mockMvc.perform(patch("/api/catalogs/brand/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"Samsung\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(service).delete(CatalogType.BRAND, 1L);

        mockMvc.perform(delete("/api/catalogs/brand/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new CatalogNotFoundException(CatalogType.BRAND, 999L)).when(service).delete(CatalogType.BRAND, 999L);

        mockMvc.perform(delete("/api/catalogs/brand/{id}", 999L))
                .andExpect(status().isNotFound());
    }
}
