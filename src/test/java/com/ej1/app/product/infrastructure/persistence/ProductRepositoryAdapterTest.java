package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.catalog.BrandEntity;
import com.ej1.app.catalog.BrandTranslationEntity;
import com.ej1.app.catalog.CategoryEntity;
import com.ej1.app.catalog.CategoryTranslationEntity;
import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({ProductRepositoryAdapter.class, ProductEntityMapperImpl.class})
class ProductRepositoryAdapterTest {

    @Autowired
    private ProductRepositoryAdapter adapter;

    @Autowired
    private ProductJpaRepository jpa;

    @Autowired
    private EntityManager em;

    private Product sample;
    private Long perifericosId;
    private Long pantallasId;
    private Long cablesId;
    private Long logitechId;

    @BeforeEach
    void setUp() {
        jpa.deleteAll();

        perifericosId = persistCategory("Periféricos");
        pantallasId = persistCategory("Pantallas");
        cablesId = persistCategory("Cables");
        logitechId = persistBrand("Logitech");

        sample = Product.builder()
                .name("Teclado mecánico")
                .description("Switches brown")
                .price(new BigDecimal("89.99"))
                .stock(10)
                .sku("KB-001")
                .categoryId(perifericosId)
                .brandId(logitechId)
                .active(true)
                .build();
    }

    private Long persistCategory(String name) {
        CategoryEntity category = CategoryEntity.builder().build();
        CategoryTranslationEntity translation = CategoryTranslationEntity.builder().category(category).build();
        translation.setLocale("en");
        translation.setName(name);
        category.setTranslations(List.of(translation));
        em.persist(category);
        return category.getId();
    }

    private Long persistBrand(String name) {
        BrandEntity brand = BrandEntity.builder().build();
        BrandTranslationEntity translation = BrandTranslationEntity.builder().brand(brand).build();
        translation.setLocale("en");
        translation.setName(name);
        brand.setTranslations(List.of(translation));
        em.persist(brand);
        return brand.getId();
    }

    @Test
    @DisplayName("save persiste y devuelve Product con id, timestamps y categoryName/brandName resueltos")
    void save_persistsAndReturnsProduct() {
        Product saved = adapter.save(sample);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getSku()).isEqualTo("KB-001");
        assertThat(saved.getActive()).isTrue();
        // save() vuelve a leer tras el insert (ver ProductRepositoryAdapter#save) precisamente
        // para que estos campos no queden null por culpa del shell de referencia de solo-id
        // que escribe ProductEntityMapper.
        assertThat(saved.getCategoryName()).isEqualTo("Periféricos");
        assertThat(saved.getBrandName()).isEqualTo("Logitech");
        assertThat(jpa.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById devuelve el Product del dominio, con categoryId/brandId y sus nombres resueltos")
    void findById_returnsDomain() {
        Product saved = adapter.save(sample);

        Optional<Product> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("KB-001");
        assertThat(result.get().getName()).isEqualTo("Teclado mecánico");
        assertThat(result.get().getCategoryId()).isEqualTo(perifericosId);
        assertThat(result.get().getCategoryName()).isEqualTo("Periféricos");
        assertThat(result.get().getBrandId()).isEqualTo(logitechId);
        assertThat(result.get().getBrandName()).isEqualTo("Logitech");
        assertThat(result.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById devuelve Optional.empty cuando no existe")
    void findById_empty() {
        Optional<Product> result = adapter.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findBySku devuelve el Product del dominio")
    void findBySku_returnsDomain() {
        adapter.save(sample);

        Optional<Product> result = adapter.findBySku("KB-001");

        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("KB-001");
    }

    @Test
    @DisplayName("findBySku devuelve Optional.empty cuando no existe")
    void findBySku_empty() {
        Optional<Product> result = adapter.findBySku("MISSING");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsBySku detecta SKU existente")
    void existsBySku_true() {
        adapter.save(sample);

        assertThat(adapter.existsBySku("KB-001")).isTrue();
    }

    @Test
    @DisplayName("existsBySku devuelve false cuando no existe")
    void existsBySku_false() {
        assertThat(adapter.existsBySku("MISSING")).isFalse();
    }

    @Test
    @DisplayName("existsById detecta id existente")
    void existsById_true() {
        Product saved = adapter.save(sample);

        assertThat(adapter.existsById(saved.getId())).isTrue();
    }

    @Test
    @DisplayName("deleteById elimina el producto")
    void deleteById_removes() {
        Product saved = adapter.save(sample);

        adapter.deleteById(saved.getId());

        assertThat(adapter.findById(saved.getId())).isEmpty();
        assertThat(jpa.count()).isZero();
    }

    @Test
    @DisplayName("search filtra por categoría (id exacto) y pagina")
    void search_filtersByCategoryId() {
        adapter.save(sample);
        adapter.save(Product.builder()
                .name("Ratón").price(new BigDecimal("29.99")).stock(50).sku("MS-001")
                .categoryId(perifericosId).active(true).build());
        adapter.save(Product.builder()
                .name("Monitor").price(new BigDecimal("199.99")).stock(3).sku("MN-001")
                .categoryId(pantallasId).active(true).build());

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, List.of(perifericosId), null, null, null, null, null),
                new Paging(0, 20, "id", "ASC"));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(Product::getSku).containsExactlyInAnyOrder("KB-001", "MS-001");
        assertThat(result.content()).extracting(Product::getCategoryName).containsOnly("Periféricos");
    }

    @Test
    @DisplayName("search filtra por categoría con varios ids (OR entre valores)")
    void search_filtersByMultipleCategoryIdsWithOr() {
        adapter.save(sample); // Periféricos
        adapter.save(Product.builder()
                .name("Ratón").price(new BigDecimal("29.99")).stock(50).sku("MS-001")
                .categoryId(perifericosId).active(true).build());
        adapter.save(Product.builder()
                .name("Monitor").price(new BigDecimal("199.99")).stock(3).sku("MN-001")
                .categoryId(pantallasId).active(true).build());
        adapter.save(Product.builder()
                .name("Cable").price(new BigDecimal("9.99")).stock(100).sku("CB-001")
                .categoryId(cablesId).active(true).build());

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, List.of(perifericosId, pantallasId), null, null, null, null, null),
                new Paging(0, 20, "id", "ASC"));

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).extracting(Product::getSku)
                .containsExactlyInAnyOrder("KB-001", "MS-001", "MN-001");
    }

    @Test
    @DisplayName("search filtra por rango de precio (inclusive)")
    void search_filtersByPriceRange() {
        adapter.save(sample); // 89.99
        adapter.save(Product.builder()
                .name("Ratón").price(new BigDecimal("29.99")).stock(50).sku("MS-001").active(true).build());
        adapter.save(Product.builder()
                .name("Monitor").price(new BigDecimal("199.99")).stock(3).sku("MN-001").active(true).build());

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, null, null, new BigDecimal("50"), new BigDecimal("100"), null, null),
                new Paging(0, 20, "id", "ASC"));

        assertThat(result.content()).extracting(Product::getSku).containsExactly("KB-001");
    }

    @Test
    @DisplayName("search filtra por active")
    void search_filtersByActive() {
        adapter.save(sample); // active = true
        adapter.save(Product.builder()
                .name("Descatalogado").price(new BigDecimal("9.99")).stock(0).sku("OLD-001").active(false).build());

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, null, null, null, null, false, null),
                new Paging(0, 20, "id", "ASC"));

        assertThat(result.content()).extracting(Product::getSku).containsExactly("OLD-001");
    }

    @Test
    @DisplayName("search ordena y pagina los resultados")
    void search_sortsAndPaginates() {
        adapter.save(sample.toBuilder().sku("KB-001").price(new BigDecimal("89.99")).build());
        adapter.save(Product.builder()
                .name("Ratón").price(new BigDecimal("29.99")).stock(50).sku("MS-001").active(true).build());
        adapter.save(Product.builder()
                .name("Monitor").price(new BigDecimal("199.99")).stock(3).sku("MN-001").active(true).build());

        PageResult<Product> firstPage = adapter.search(new ProductSearchCriteria(
                null, null, null, null, null, null, null, null),
                new Paging(0, 2, "price", "ASC"));

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content()).extracting(Product::getSku).containsExactly("MS-001", "KB-001");

        PageResult<Product> secondPage = adapter.search(new ProductSearchCriteria(
                null, null, null, null, null, null, null, null),
                new Paging(1, 2, "price", "ASC"));

        assertThat(secondPage.content()).extracting(Product::getSku).containsExactly("MN-001");
    }

    @Test
    @DisplayName("search ordena por categoryId (vía join con la tabla de categorías)")
    void search_sortsByCategoryId() {
        adapter.save(Product.builder()
                .name("Cable").price(new BigDecimal("9.99")).stock(100).sku("CB-001")
                .categoryId(cablesId).active(true).build());
        adapter.save(sample); // Periféricos

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, null, null, null, null, null, null),
                new Paging(0, 20, "categoryId", "ASC"));

        assertThat(result.content()).extracting(Product::getSku).containsExactly("KB-001", "CB-001");
    }

    @Test
    @DisplayName("search devuelve página vacía cuando no hay coincidencias")
    void search_returnsEmptyPageWhenNoMatches() {
        adapter.save(sample);

        PageResult<Product> result = adapter.search(new ProductSearchCriteria(
                null, null, List.of(999999L), null, null, null, null, null),
                new Paging(0, 20, "id", "ASC"));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("save con id existente actualiza el producto")
    void save_updatesExisting() {
        Product saved = adapter.save(sample);
        Product modified = saved.withIdAndTimestamps(saved.getId(), saved.getCreatedAt(), saved.getUpdatedAt())
                .toBuilder()
                .name("Teclado RGB")
                .stock(5)
                .build();

        Product updated = adapter.save(modified);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getName()).isEqualTo("Teclado RGB");
        assertThat(updated.getStock()).isEqualTo(5);
        assertThat(jpa.count()).isEqualTo(1);
    }
}
