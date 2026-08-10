package com.ej1.app.product.interfaces.rest;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.domain.service.ProductService;
import com.ej1.app.product.infrastructure.exception.ApiExceptionHandler;
import com.ej1.app.product.infrastructure.rest.ProductRestMapperAdapterImpl;
import com.ej1.app.product.interfaces.rest.dto.ProductRequest;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({ApiExceptionHandler.class, ProductRestMapperAdapterImpl.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService service;

    private Product buildEntity(Long id, String sku) {
        return Product.builder()
                .id(id)
                .name("Teclado mecánico")
                .description("Switches brown")
                .price(new BigDecimal("89.99"))
                .stock(10)
                .sku(sku)
                .category("Periféricos")
                .brand("Logitech")
                .imageUrl("https://example.com/kb.jpg")
                .weight(new BigDecimal("1.200"))
                .dimensions("45x15x3 cm")
                .tags("teclado,rgb")
                .active(true)
                .build();
    }

    private ProductRequest buildRequest(String sku) {
        return ProductRequest.builder()
                .name("Teclado mecánico")
                .description("Switches brown")
                .price(new BigDecimal("89.99"))
                .stock(10)
                .sku(sku)
                .category("Periféricos")
                .brand("Logitech")
                .active(true)
                .build();
    }

    @Test
    void getAll_returns200WithList() throws Exception {
        when(service.findAll()).thenReturn(List.of(buildEntity(1L, "KB-001")));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sku").value("KB-001"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void getById_returns200WhenFound() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(get("/api/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("KB-001"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/products/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found: id=999"));
    }

    @Test
    void getBySku_returns200WhenFound() throws Exception {
        when(service.findBySku("KB-001")).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(get("/api/products/sku/{sku}", "KB-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("KB-001"));
    }

    @Test
    void getBySku_returns404WhenNotFound() throws Exception {
        when(service.findBySku("MISSING")).thenThrow(new ProductNotFoundException("MISSING", true));

        mockMvc.perform(get("/api/products/sku/{sku}", "MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: sku=MISSING"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(service.create(any(CreateProductCommand.class))).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KB-001"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/products/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("KB-001"));
    }

    @Test
    void create_returns400WhenPayloadInvalid() throws Exception {
        ProductRequest invalid = ProductRequest.builder()
                .name("")
                .price(new BigDecimal("-1"))
                .stock(-5)
                .sku("")
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void create_returns409WhenSkuDuplicated() throws Exception {
        when(service.create(any(CreateProductCommand.class)))
                .thenThrow(new DuplicateSkuException("KB-001"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KB-001"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Product with sku 'KB-001' already exists"));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        Product updated = Product.builder()
                .id(1L)
                .name("Teclado RGB")
                .price(new BigDecimal("99.99"))
                .stock(8)
                .sku("KB-001")
                .active(true)
                .build();
        when(service.update(eq(1L), any(UpdateProductCommand.class))).thenReturn(updated);

        mockMvc.perform(put("/api/products/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KB-001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Teclado RGB"))
                .andExpect(jsonPath("$.price").value(99.99))
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        when(service.update(eq(999L), any(UpdateProductCommand.class)))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(put("/api/products/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KB-001"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new ProductNotFoundException(999L)).when(service).delete(999L);

        mockMvc.perform(delete("/api/products/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: id=999"));
    }
}
