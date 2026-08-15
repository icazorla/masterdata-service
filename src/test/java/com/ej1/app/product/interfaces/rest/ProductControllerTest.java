package com.ej1.app.product.interfaces.rest;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.InvalidProductQueryException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductPage;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.domain.service.ProductService;
import com.ej1.app.product.infrastructure.exception.ApiExceptionHandler;
import com.ej1.app.product.infrastructure.rest.ProductRestMapperAdapterImpl;
import com.ej1.app.product.interfaces.rest.dto.ProductQueryRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductRequest;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import com.ej1.app.shared.rest.dto.PagingRequest;
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

import java.math.BigDecimal;
import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({ApiExceptionHandler.class, JsonPatchExceptionHandler.class, ProductRestMapperAdapterImpl.class, JsonPatchApplier.class})
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
    void getAll_returns200WithPagedResults() throws Exception {
        ProductPage page = new ProductPage(List.of(buildEntity(1L, "KB-001")), 0, 20, 1, 1);
        when(service.search(any(ProductSearchCriteria.class))).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KB-001"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getAll_honoursPageAndSizeParams() throws Exception {
        ProductPage page = new ProductPage(List.of(), 2, 5, 0, 0);
        when(service.search(any(ProductSearchCriteria.class))).thenReturn(page);

        mockMvc.perform(get("/api/products").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(service).search(argThat(criteria -> criteria.page() == 2 && criteria.size() == 5));
    }

    @Test
    void getAll_returns400WhenSizeExceedsMax() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getAll_returns422WhenSortByIsUnknown() throws Exception {
        when(service.search(any(ProductSearchCriteria.class)))
                .thenThrow(new InvalidProductQueryException("sortBy 'notAField' is not sortable. Allowed values: [...]"));

        mockMvc.perform(get("/api/products").param("sortBy", "notAField"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
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
    void create_returns400WhenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
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
    void query_returns200WithPagedResults() throws Exception {
        ProductPage page = new ProductPage(List.of(buildEntity(1L, "KB-001")), 0, 20, 1, 1);
        when(service.search(any(ProductSearchCriteria.class))).thenReturn(page);

        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductQueryRequest.builder()
                                .category(List.of("Periféricos"))
                                .minPrice(new BigDecimal("10"))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Query", "application/json"))
                .andExpect(jsonPath("$.content[0].sku").value("KB-001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void query_returns200WithDefaultsWhenBodyIsEmptyObject() throws Exception {
        when(service.search(any(ProductSearchCriteria.class)))
                .thenReturn(new ProductPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void query_returns400WhenSizeExceedsMax() throws Exception {
        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductQueryRequest.builder()
                                .page(PagingRequest.builder().size(500).build())
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void query_returns200WhenPageIsGroupedInJson() throws Exception {
        ProductPage page = new ProductPage(List.of(), 2, 5, 0, 0);
        when(service.search(any(ProductSearchCriteria.class))).thenReturn(page);

        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":{\"number\":2,\"size\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(service).search(argThat(criteria -> criteria.page() == 2 && criteria.size() == 5));
    }

    @Test
    void query_returns422WhenSortByIsUnknown() throws Exception {
        when(service.search(any(ProductSearchCriteria.class)))
                .thenThrow(new InvalidProductQueryException("sortBy 'notAField' is not sortable. Allowed values: [...]"));

        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductQueryRequest.builder()
                                .page(PagingRequest.builder().sortBy("notAField").build())
                                .build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void query_returns415WhenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("category=Periféricos"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void query_returns422WhenMinPriceGreaterThanMaxPrice() throws Exception {
        when(service.search(any(ProductSearchCriteria.class)))
                .thenThrow(new InvalidProductQueryException("minPrice (100) must not be greater than maxPrice (10)"));

        mockMvc.perform(post("/api/products/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductQueryRequest.builder()
                                .minPrice(new BigDecimal("100"))
                                .maxPrice(new BigDecimal("10"))
                                .build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
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
    void patch_returns200WithUpdatedField() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));
        Product updated = buildEntity(1L, "KB-001").toBuilder().price(new BigDecimal("79.99")).build();
        when(service.update(eq(1L), any(UpdateProductCommand.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/price\",\"value\":79.99}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(79.99));
    }

    @Test
    void patch_returns404WhenNotFound() throws Exception {
        when(service.findById(999L)).thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(patch("/api/products/{id}", 999L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/price\",\"value\":10}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_returns415WhenContentTypeIsNotJsonPatch() throws Exception {
        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"op\":\"replace\",\"path\":\"/price\",\"value\":79.99}]"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void patch_returns400WhenPathDoesNotExist() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/doesNotExist\",\"value\":1}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_returns400WhenResultingPayloadInvalid() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/price\",\"value\":-5}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void patch_returns409WhenTestOperationFails() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));

        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"test\",\"path\":\"/sku\",\"value\":\"WRONG-SKU\"}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void patch_returns409WhenSkuDuplicated() throws Exception {
        when(service.findById(1L)).thenReturn(buildEntity(1L, "KB-001"));
        when(service.update(eq(1L), any(UpdateProductCommand.class)))
                .thenThrow(new DuplicateSkuException("KB-999"));

        mockMvc.perform(patch("/api/products/{id}", 1L)
                        .contentType("application/json-patch+json")
                        .content("[{\"op\":\"replace\",\"path\":\"/sku\",\"value\":\"KB-999\"}]"))
                .andExpect(status().isConflict());
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
