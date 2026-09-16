package com.ej1.app.brand.interfaces.rest;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.exception.BrandNotFoundException;
import com.ej1.app.brand.domain.exception.DuplicateBrandNameException;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import com.ej1.app.brand.domain.service.BrandService;
import com.ej1.app.brand.infrastructure.rest.BrandRestMapperAdapterImpl;
import com.ej1.app.brand.interfaces.rest.dto.BrandRequest;
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

@WebMvcTest(BrandController.class)
@Import({ApiExceptionHandler.class, BrandRestMapperAdapterImpl.class})
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BrandService service;

    private Brand buildEntity(Long id, String name) {
        return Brand.builder().id(id).name(name).description("desc").build();
    }

    private BrandRequest buildRequest(String name) {
        return BrandRequest.builder().name(name).description("desc").build();
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
    void getById_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new BrandNotFoundException(999L));

        mockMvc.perform(get("/api/brands/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Brand not found: id=999"));
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
        BrandRequest invalid = BrandRequest.builder().name("").build();

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
