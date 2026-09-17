package com.ej1.app.brand.interfaces.rest;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.BrandTranslation;
import com.ej1.app.brand.domain.exception.BrandNotFoundException;
import com.ej1.app.brand.domain.exception.DuplicateBrandNameException;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import com.ej1.app.brand.domain.service.BrandService;
import com.ej1.app.brand.infrastructure.rest.BrandRestMapperAdapterImpl;
import com.ej1.app.brand.interfaces.rest.dto.BrandRequest;
import com.ej1.app.brand.interfaces.rest.dto.BrandTranslationRequest;
import com.ej1.app.shared.rest.exception.ApiExceptionHandler;
import com.ej1.app.shared.rest.exception.JsonPatchExceptionHandler;
import com.ej1.app.shared.rest.patch.JsonPatchApplier;
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

@WebMvcTest(BrandController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, BrandRestMapperAdapterImpl.class, JsonPatchApplier.class})
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BrandService service;

    private Brand buildEntity(Long id, String name) {
        return Brand.builder().id(id).translations(List.of(new BrandTranslation("en", name, "desc"))).build();
    }

    private BrandRequest buildRequest(String name) {
        return BrandRequest.builder()
                .translations(Map.of("en", BrandTranslationRequest.builder().name(name).description("desc").build()))
                .build();
    }

    @Test
    void getAll_returns200WithList() throws Exception {
        when(service.findAll()).thenReturn(List.of(buildEntity(1L, "Logitech")));

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Logitech"));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(get("/api/brands/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_fallsBackToDefaultLocaleWhenTranslationMissing() throws Exception {
        Brand brand = Brand.builder().id(1L)
                .translations(List.of(new BrandTranslation("en", "Logitech", "desc")))
                .build();
        when(service.findById(1L)).thenReturn(brand);

        mockMvc.perform(get("/api/brands/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new BrandNotFoundException(999L));

        mockMvc.perform(get("/api/brands/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Brand not found: id=999"));
    }

    @Test
    void getById_returns404WithSpanishMessageWhenAcceptLanguageIsSpanish() throws Exception {
        when(service.findById(999L)).thenThrow(new BrandNotFoundException(999L));

        mockMvc.perform(get("/api/brands/{id}", 999L).header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Marca no encontrada: id=999"));
    }

    @Test
    void getTranslations_returns200WithEveryLocaleKeyed() throws Exception {
        Brand brand = Brand.builder().id(1L)
                .translations(List.of(
                        new BrandTranslation("en", "Logitech", "Peripheral manufacturer"),
                        new BrandTranslation("es", "Logitech", "Fabricante de periféricos")))
                .build();
        when(service.findById(1L)).thenReturn(brand);

        mockMvc.perform(get("/api/brands/{id}/translations", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en.name").value("Logitech"))
                .andExpect(jsonPath("$.es.description").value("Fabricante de periféricos"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any(CreateBrandCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/brands/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        BrandRequest invalid = BrandRequest.builder().translations(Map.of()).build();

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenDefaultLocaleMissing() throws Exception {
        BrandRequest invalid = BrandRequest.builder()
                .translations(Map.of("es", BrandTranslationRequest.builder().name("Logitech").build()))
                .build();

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenLocaleNotSupported() throws Exception {
        BrandRequest invalid = BrandRequest.builder()
                .translations(Map.of(
                        "en", BrandTranslationRequest.builder().name("Logitech").build(),
                        "fr", BrandTranslationRequest.builder().name("Logitech").build()))
                .build();

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(service.create(any(CreateBrandCommand.class)))
                .thenThrow(new DuplicateBrandNameException("Logitech"));

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(service.update(eq(1L), any(UpdateBrandCommand.class))).thenReturn(buildEntity(1L, "Logitech Pro"));

        mockMvc.perform(put("/api/brands/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Logitech Pro"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(service.update(eq(999L), any(UpdateBrandCommand.class)))
                .thenThrow(new BrandNotFoundException(999L));

        mockMvc.perform(put("/api/brands/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Logitech"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_replacesTranslationFieldByLocale() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(1L), any(UpdateBrandCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"New description\"}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), argThat(cmd -> cmd.translations().stream()
                .anyMatch(t -> t.locale().equals("en") && "New description".equals(t.description()))));
    }

    @Test
    void patch_addsNewLocaleByKey() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(1L), any(UpdateBrandCommand.class))).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"add\",\"path\":\"/translations/es\","
                                + "\"value\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), argThat(cmd -> cmd.translations().size() == 2
                && cmd.translations().stream().anyMatch(t -> t.locale().equals("es"))));
    }

    @Test
    void patch_returns400WhenRemovingDefaultLocale() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"remove\",\"path\":\"/translations/en\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new BrandNotFoundException(999L));

        mockMvc.perform(patch("/api/brands/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));

        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns409WhenNameDuplicated() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Logitech"));
        when(service.update(eq(1L), any(UpdateBrandCommand.class)))
                .thenThrow(new DuplicateBrandNameException("Samsung"));

        mockMvc.perform(patch("/api/brands/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"Samsung\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/brands/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new BrandNotFoundException(999L)).when(service).delete(999L);

        mockMvc.perform(delete("/api/brands/{id}", 999L))
                .andExpect(status().isNotFound());
    }
}
