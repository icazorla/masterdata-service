package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({CategoryRepositoryAdapter.class, CategoryEntityMapperImpl.class})
class CategoryRepositoryAdapterTest {

    @Autowired
    private CategoryRepositoryAdapter adapter;

    @Autowired
    private CategoryJpaRepository jpa;

    private Category sample;

    @BeforeEach
    void setUp() {
        jpa.deleteAll();
        sample = Category.builder()
                .name("Periféricos")
                .description("Teclados, ratones y accesorios")
                .build();
    }

    @Test
    @DisplayName("save persiste y devuelve Category con id y timestamps")
    void save_persistsAndReturnsCategory() {
        Category saved = adapter.save(sample);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Periféricos");
    }

    @Test
    @DisplayName("findById devuelve la Category del dominio")
    void findById_returnsDomain() {
        Category saved = adapter.save(sample);

        Optional<Category> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Periféricos");
    }

    @Test
    @DisplayName("findById devuelve Optional.empty cuando no existe")
    void findById_empty() {
        assertThat(adapter.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("existsByNameIgnoreCase detecta nombre existente sin distinguir mayúsculas")
    void existsByNameIgnoreCase_true() {
        adapter.save(sample);

        assertThat(adapter.existsByNameIgnoreCase("periféricos")).isTrue();
    }

    @Test
    @DisplayName("existsByNameIgnoreCase devuelve false cuando no existe")
    void existsByNameIgnoreCase_false() {
        assertThat(adapter.existsByNameIgnoreCase("no-existe")).isFalse();
    }

    @Test
    @DisplayName("existsById detecta id existente")
    void existsById_true() {
        Category saved = adapter.save(sample);

        assertThat(adapter.existsById(saved.getId())).isTrue();
    }

    @Test
    @DisplayName("findAll devuelve todas las categorías ordenadas por nombre")
    void findAll_returnsAllSortedByName() {
        adapter.save(Category.builder().name("Pantallas").build());
        adapter.save(Category.builder().name("Cables").build());
        adapter.save(sample); // Periféricos

        List<Category> result = adapter.findAll();

        assertThat(result).extracting(Category::getName).containsExactly("Cables", "Pantallas", "Periféricos");
    }

    @Test
    @DisplayName("deleteById elimina la categoría")
    void deleteById_removes() {
        Category saved = adapter.save(sample);

        adapter.deleteById(saved.getId());

        assertThat(adapter.findById(saved.getId())).isEmpty();
        assertThat(jpa.count()).isZero();
    }

    @Test
    @DisplayName("save con id existente actualiza la categoría")
    void save_updatesExisting() {
        Category saved = adapter.save(sample);
        Category modified = saved.withIdAndTimestamps(saved.getId(), saved.getCreatedAt(), saved.getUpdatedAt())
                .toBuilder()
                .description("Nueva descripción")
                .build();

        Category updated = adapter.save(modified);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getDescription()).isEqualTo("Nueva descripción");
        assertThat(jpa.count()).isEqualTo(1);
    }
}
