package com.ej1.app.category.interfaces.rest;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.CategoryTranslation;
import com.ej1.app.category.domain.exception.CategoryNotFoundException;
import com.ej1.app.category.domain.exception.DuplicateCategoryNameException;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import com.ej1.app.category.domain.service.CategoryService;
import com.ej1.app.category.infrastructure.rest.CategoryRestMapperAdapterImpl;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryTranslationRequest;
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

@WebMvcTest(CategoryController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, CategoryRestMapperAdapterImpl.class, JsonPatchApplier.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService service;

    private Category buildEntity(Long id, String name) {
        return Category.builder().id(id).translations(List.of(new CategoryTranslation("en", name, "desc"))).build();
    }

    private CategoryRequest buildRequest(String name) {
        return CategoryRequest.builder()
                .translations(Map.of("en", CategoryTranslationRequest.builder().name(name).description("desc").build()))
                .build();
    }

    @Test
    void getAll_returns200WithList() throws Exception {
        when(service.findAll()).thenReturn(List.of(buildEntity(1L, "Peripherals")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Peripherals"));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(get("/api/categories/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Peripherals"));
    }

    @Test
    void getById_fallsBackToDefaultLocaleWhenTranslationMissing() throws Exception {
        Category category = Category.builder().id(1L)
                .translations(List.of(new CategoryTranslation("en", "Peripherals", "desc")))
                .build();
        when(service.findById(1L)).thenReturn(category);

        mockMvc.perform(get("/api/categories/{id}", 1L).header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Peripherals"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(get("/api/categories/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Category not found: id=999"));
    }

    @Test
    void getById_returns404WithSpanishMessageWhenAcceptLanguageIsSpanish() throws Exception {
        when(service.findById(999L)).thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(get("/api/categories/{id}", 999L).header("Accept-Language", "es"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoría no encontrada: id=999"));
    }

    @Test
    void getTranslations_returns200WithEveryLocaleKeyed() throws Exception {
        Category category = Category.builder().id(1L)
                .translations(List.of(
                        new CategoryTranslation("en", "Peripherals", "Keyboards, mice and accessories"),
                        new CategoryTranslation("es", "Periféricos", "Teclados, ratones y accesorios")))
                .build();
        when(service.findById(1L)).thenReturn(category);

        mockMvc.perform(get("/api/categories/{id}/translations", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en.name").value("Peripherals"))
                .andExpect(jsonPath("$.es.description").value("Teclados, ratones y accesorios"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any(CreateCategoryCommand.class))).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Peripherals"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/categories/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        CategoryRequest invalid = CategoryRequest.builder().translations(Map.of()).build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenDefaultLocaleMissing() throws Exception {
        CategoryRequest invalid = CategoryRequest.builder()
                .translations(Map.of("es", CategoryTranslationRequest.builder().name("Periféricos").build()))
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns400WhenLocaleNotSupported() throws Exception {
        CategoryRequest invalid = CategoryRequest.builder()
                .translations(Map.of(
                        "en", CategoryTranslationRequest.builder().name("Peripherals").build(),
                        "fr", CategoryTranslationRequest.builder().name("Peripherals").build()))
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(service.create(any(CreateCategoryCommand.class)))
                .thenThrow(new DuplicateCategoryNameException("Peripherals"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Peripherals"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(service.update(eq(1L), any(UpdateCategoryCommand.class))).thenReturn(buildEntity(1L, "Peripherals Pro"));

        mockMvc.perform(put("/api/categories/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Peripherals Pro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Peripherals Pro"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(service.update(eq(999L), any(UpdateCategoryCommand.class)))
                .thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(put("/api/categories/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Peripherals"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_replacesTranslationFieldByLocale() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));
        when(service.update(eq(1L), any(UpdateCategoryCommand.class))).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/description\",\"value\":\"New description\"}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), argThat(cmd -> cmd.translations().stream()
                .anyMatch(t -> t.locale().equals("en") && "New description".equals(t.description()))));
    }

    @Test
    void patch_addsNewLocaleByKey() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));
        when(service.update(eq(1L), any(UpdateCategoryCommand.class))).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"add\",\"path\":\"/translations/es\","
                                + "\"value\":{\"name\":\"Periféricos\",\"description\":\"Teclados, ratones y accesorios\"}}]"))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), argThat(cmd -> cmd.translations().size() == 2
                && cmd.translations().stream().anyMatch(t -> t.locale().equals("es"))));
    }

    @Test
    void patch_returns400WhenRemovingDefaultLocale() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"remove\",\"path\":\"/translations/en\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(patch("/api/categories/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"X\"}]"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));

        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns409WhenNameDuplicated() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Peripherals"));
        when(service.update(eq(1L), any(UpdateCategoryCommand.class)))
                .thenThrow(new DuplicateCategoryNameException("Displays"));

        mockMvc.perform(patch("/api/categories/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/translations/en/name\",\"value\":\"Displays\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/categories/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new CategoryNotFoundException(999L)).when(service).delete(999L);

        mockMvc.perform(delete("/api/categories/{id}", 999L))
                .andExpect(status().isNotFound());
    }
}
