package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductRepository;
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

    private Product sample;

    @BeforeEach
    void setUp() {
        jpa.deleteAll();
        sample = Product.builder()
                .name("Teclado mecánico")
                .description("Switches brown")
                .price(new BigDecimal("89.99"))
                .stock(10)
                .sku("KB-001")
                .category("Periféricos")
                .brand("Logitech")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("save persiste y devuelve Product con id y timestamps")
    void save_persistsAndReturnsProduct() {
        Product saved = adapter.save(sample);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getSku()).isEqualTo("KB-001");
        assertThat(saved.getActive()).isTrue();
        assertThat(jpa.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById devuelve el Product del dominio")
    void findById_returnsDomain() {
        Product saved = adapter.save(sample);

        Optional<Product> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("KB-001");
        assertThat(result.get().getName()).isEqualTo("Teclado mecánico");
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
    @DisplayName("findAll mapea todos los productos")
    void findAll_mapsAll() {
        adapter.save(sample);
        adapter.save(Product.builder()
                .name("Ratón")
                .price(new BigDecimal("29.99"))
                .stock(50)
                .sku("MS-001")
                .active(true)
                .build());

        List<Product> result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getSku).containsExactlyInAnyOrder("KB-001", "MS-001");
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
