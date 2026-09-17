package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.CategoryTranslation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({CategoryRepositoryAdapter.class, CategoryEntityMapperImpl.class})
@TestPropertySource(properties = "spring.web.locale=en")
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
                .translations(List.of(new CategoryTranslation("en", "Peripherals", "Keyboards, mice and accessories")))
                .build();
    }

    @Test
    @DisplayName("save persiste y devuelve Category con id y timestamps")
    void save_persistsAndReturnsCategory() {
        Category saved = adapter.save(sample);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.translation("en")).isPresent();
        assertThat(saved.translation("en").get().name()).isEqualTo("Peripherals");
    }

    @Test
    @DisplayName("findById devuelve la Category del dominio")
    void findById_returnsDomain() {
        Category saved = adapter.save(sample);

        Optional<Category> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().translation("en").get().name()).isEqualTo("Peripherals");
    }

    @Test
    @DisplayName("findById devuelve Optional.empty cuando no existe")
    void findById_empty() {
        assertThat(adapter.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("existsByLocaleAndNameIgnoreCase detecta nombre existente sin distinguir mayúsculas")
    void existsByLocaleAndNameIgnoreCase_true() {
        adapter.save(sample);

        assertThat(adapter.existsByLocaleAndNameIgnoreCase("en", "peripherals")).isTrue();
    }

    @Test
    @DisplayName("existsByLocaleAndNameIgnoreCase devuelve false cuando no existe")
    void existsByLocaleAndNameIgnoreCase_false() {
        assertThat(adapter.existsByLocaleAndNameIgnoreCase("en", "no-existe")).isFalse();
    }

    @Test
    @DisplayName("existsByLocaleAndNameIgnoreCaseAndIdNot ignora la propia categoría")
    void existsByLocaleAndNameIgnoreCaseAndIdNot_excludesSelf() {
        Category saved = adapter.save(sample);

        assertThat(adapter.existsByLocaleAndNameIgnoreCaseAndIdNot("en", "Peripherals", saved.getId())).isFalse();
    }

    @Test
    @DisplayName("existsById detecta id existente")
    void existsById_true() {
        Category saved = adapter.save(sample);

        assertThat(adapter.existsById(saved.getId())).isTrue();
    }

    @Test
    @DisplayName("findAll devuelve todas las categorías ordenadas por nombre en el idioma por defecto")
    void findAll_returnsAllSortedByName() {
        adapter.save(Category.builder().translations(List.of(new CategoryTranslation("en", "Screens", null))).build());
        adapter.save(Category.builder().translations(List.of(new CategoryTranslation("en", "Cables", null))).build());
        adapter.save(sample); // Peripherals

        List<Category> result = adapter.findAll();

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Cables", "Peripherals", "Screens");
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
                .translations(List.of(new CategoryTranslation("en", "Peripherals", "New description")))
                .build();

        Category updated = adapter.save(modified);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.translation("en").get().description()).isEqualTo("New description");
        assertThat(jpa.count()).isEqualTo(1);
    }
}
