package com.ej1.app.category.interfaces.rest;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.exception.CategoryNotFoundException;
import com.ej1.app.category.domain.exception.DuplicateCategoryNameException;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import com.ej1.app.category.domain.service.CategoryService;
import com.ej1.app.category.infrastructure.rest.CategoryRestMapperAdapterImpl;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.shared.rest.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({ApiExceptionHandler.class, CategoryRestMapperAdapterImpl.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService service;

    private Category buildEntity(Long id, String name) {
        return Category.builder().id(id).name(name).description("desc").build();
    }

    private CategoryRequest buildRequest(String name) {
        return CategoryRequest.builder().name(name).description("desc").build();
    }

    @Test
    void getAll_returns200WithList() throws Exception {
        when(service.findAll()).thenReturn(List.of(buildEntity(1L, "Periféricos")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Periféricos"));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "Periféricos"));

        mockMvc.perform(get("/api/categories/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Periféricos"));
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
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any(CreateCategoryCommand.class))).thenReturn(buildEntity(1L, "Periféricos"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Periféricos"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/categories/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        CategoryRequest invalid = CategoryRequest.builder().name("").build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns409WhenNameDuplicated() throws Exception {
        when(service.create(any(CreateCategoryCommand.class)))
                .thenThrow(new DuplicateCategoryNameException("Periféricos"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Periféricos"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        when(service.update(eq(1L), any(UpdateCategoryCommand.class))).thenReturn(buildEntity(1L, "Periféricos RGB"));

        mockMvc.perform(put("/api/categories/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Periféricos RGB"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Periféricos RGB"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(service.update(eq(999L), any(UpdateCategoryCommand.class)))
                .thenThrow(new CategoryNotFoundException(999L));

        mockMvc.perform(put("/api/categories/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Periféricos"))))
                .andExpect(status().isNotFound());
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
