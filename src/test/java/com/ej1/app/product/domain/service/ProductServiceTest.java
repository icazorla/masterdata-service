package com.ej1.app.product.domain.service;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.service.CatalogService;
import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.InvalidProductQueryException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductMapper;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.infrastructure.persistence.ProductMapperAdapter;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Spy
    private ProductMapper mapper = Mappers.getMapper(ProductMapperAdapter.class);

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ProductService service;

    private Product sample;
    private CreateProductCommand validCreate;
    private UpdateProductCommand validUpdate;

    @BeforeEach
    void setUp() {
        sample = Product.builder()
                .id(1L)
                .name("Teclado mecánico")
                .description("Switches brown")
                .price(new BigDecimal("89.99"))
                .stock(10)
                .sku("KB-001")
                .categoryId(1L)
                .brandId(3L)
                .active(true)
                .build();

        validCreate = new CreateProductCommand(
                "Teclado mecánico",
                "Switches brown",
                new BigDecimal("89.99"),
                10,
                "KB-001",
                1L,
                3L,
                null,
                null,
                null,
                null,
                true);

        validUpdate = new UpdateProductCommand(
                "Teclado mecánico",
                "Switches brown",
                new BigDecimal("89.99"),
                10,
                "KB-001",
                1L,
                3L,
                null,
                null,
                null,
                null,
                true);
    }

    @Test
    @DisplayName("findById devuelve el producto cuando existe")
    void findById_found() {
        when(repository.findById(1L)).thenReturn(Optional.of(sample));

        Product result = service.findById(1L);

        assertThat(result).isEqualTo(sample);
    }

    @Test
    @DisplayName("findById lanza ProductNotFoundException cuando no existe")
    void findById_notFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findBySku devuelve el producto cuando existe")
    void findBySku_found() {
        when(repository.findBySku("KB-001")).thenReturn(Optional.of(sample));

        Product result = service.findBySku("KB-001");

        assertThat(result).isEqualTo(sample);
    }

    @Test
    @DisplayName("findBySku lanza ProductNotFoundException cuando no existe")
    void findBySku_notFound() {
        when(repository.findBySku("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySku("MISSING"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    @DisplayName("create persiste el producto cuando el SKU es nuevo")
    void create_persistsWhenSkuNew() {
        when(repository.existsBySku("KB-001")).thenReturn(false);
        when(catalogService.existsById(CatalogType.CATEGORY, 1L)).thenReturn(true);
        when(catalogService.existsById(CatalogType.BRAND, 3L)).thenReturn(true);
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.create(validCreate);

        assertThat(result.getSku()).isEqualTo("KB-001");
        assertThat(result.getActive()).isTrue();
        verify(repository).existsBySku("KB-001");
        verify(repository).save(any(Product.class));
    }

    @Test
    @DisplayName("create lanza DuplicateSkuException cuando el SKU ya existe")
    void create_throwsWhenSkuDuplicated() {
        when(repository.existsBySku("KB-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("KB-001");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create lanza CatalogNotFoundException cuando categoryId no existe")
    void create_throwsWhenCategoryDoesNotExist() {
        when(repository.existsBySku("KB-001")).thenReturn(false);
        when(catalogService.existsById(CatalogType.CATEGORY, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("1");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create lanza CatalogNotFoundException cuando brandId no existe")
    void create_throwsWhenBrandDoesNotExist() {
        when(repository.existsBySku("KB-001")).thenReturn(false);
        when(catalogService.existsById(CatalogType.CATEGORY, 1L)).thenReturn(true);
        when(catalogService.existsById(CatalogType.BRAND, 3L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("3");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create aplica active=true por defecto cuando el command lo omite")
    void create_appliesDefaultActive() {
        CreateProductCommand withoutActive = new CreateProductCommand(
                "Teclado mecánico", "Switches brown",
                new BigDecimal("89.99"), 10, "KB-001",
                1L, 3L,
                null, null, null, null,
                null);
        when(repository.existsBySku(anyString())).thenReturn(false);
        when(catalogService.existsById(CatalogType.CATEGORY, 1L)).thenReturn(true);
        when(catalogService.existsById(CatalogType.BRAND, 3L)).thenReturn(true);
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.create(withoutActive);

        assertThat(result.getActive()).isTrue();
    }

    @Test
    @DisplayName("update modifica los campos cuando el SKU no cambia")
    void update_modifiesWhenSkuUnchanged() {
        UpdateProductCommand updateCmd = new UpdateProductCommand(
                "Teclado RGB", null,
                new BigDecimal("99.99"), 5, "KB-001",
                null, null, null, null, null, null, true);
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.update(1L, updateCmd);

        assertThat(result.getName()).isEqualTo("Teclado RGB");
        assertThat(result.getPrice()).isEqualByComparingTo("99.99");
        assertThat(result.getStock()).isEqualTo(5);
        assertThat(result.getSku()).isEqualTo("KB-001");
        verify(repository, never()).existsBySku(anyString());
    }

    @Test
    @DisplayName("update detecta duplicado cuando el SKU cambia a uno existente")
    void update_throwsWhenNewSkuExists() {
        UpdateProductCommand updateCmd = new UpdateProductCommand(
                "Teclado RGB", null,
                new BigDecimal("99.99"), 5, "KB-002",
                null, null, null, null, null, null, true);
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.existsBySku("KB-002")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, updateCmd))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("KB-002");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update permite cambiar a un SKU nuevo que no existe")
    void update_allowsSkuChangeToNew() {
        UpdateProductCommand updateCmd = new UpdateProductCommand(
                "Teclado RGB", null,
                new BigDecimal("99.99"), 5, "KB-NEW",
                null, null, null, null, null, null, true);
        when(repository.findById(1L)).thenReturn(Optional.of(sample));
        when(repository.existsBySku("KB-NEW")).thenReturn(false);
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.update(1L, updateCmd);

        assertThat(result.getSku()).isEqualTo("KB-NEW");
    }

    @Test
    @DisplayName("update lanza ProductNotFoundException cuando el id no existe")
    void update_throwsWhenIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, validUpdate))
                .isInstanceOf(ProductNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("search delega en el repositorio y devuelve su resultado")
    void search_delegatesToRepository() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                "teclado", null, null, null, null, null, null, null);
        Paging paging = new Paging(0, 20, "id", "ASC");
        PageResult<Product> page = new PageResult<>(List.of(sample), 0, 20, 1, 1);
        when(repository.search(criteria, paging)).thenReturn(page);

        PageResult<Product> result = service.search(criteria, paging);

        assertThat(result).isEqualTo(page);
        verify(repository).search(criteria, paging);
    }

    @Test
    @DisplayName("search lanza InvalidProductQueryException cuando minPrice > maxPrice")
    void search_throwsWhenMinPriceGreaterThanMaxPrice() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, new BigDecimal("100"), new BigDecimal("10"),
                null, null);
        Paging paging = new Paging(0, 20, "id", "ASC");

        assertThatThrownBy(() -> service.search(criteria, paging))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("10");

        verify(repository, never()).search(any(), any());
    }

    @Test
    @DisplayName("search permite minPrice == maxPrice")
    void search_allowsMinPriceEqualToMaxPrice() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, new BigDecimal("50"), new BigDecimal("50"),
                null, null);
        Paging paging = new Paging(0, 20, "id", "ASC");
        PageResult<Product> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(repository.search(criteria, paging)).thenReturn(page);

        PageResult<Product> result = service.search(criteria, paging);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("search lanza InvalidProductQueryException cuando sortBy no es un campo ordenable")
    void search_throwsWhenSortByIsUnknown() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, null, null, null, null);
        Paging paging = new Paging(0, 20, "notAField", "ASC");

        assertThatThrownBy(() -> service.search(criteria, paging))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessageContaining("notAField");

        verify(repository, never()).search(any(), any());
    }

    @Test
    @DisplayName("delete borra cuando el id existe")
    void delete_removesWhenExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("delete lanza ProductNotFoundException cuando el id no existe")
    void delete_throwsWhenNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(repository, never()).deleteById(anyLong());
    }
}
