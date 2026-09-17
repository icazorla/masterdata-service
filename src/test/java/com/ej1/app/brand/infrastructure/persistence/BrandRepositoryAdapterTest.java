package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.BrandTranslation;
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
@Import({BrandRepositoryAdapter.class, BrandEntityMapperImpl.class})
@TestPropertySource(properties = "spring.web.locale=en")
class BrandRepositoryAdapterTest {

    @Autowired
    private BrandRepositoryAdapter adapter;

    @Autowired
    private BrandJpaRepository jpa;

    private Brand sample;

    @BeforeEach
    void setUp() {
        jpa.deleteAll();
        sample = Brand.builder()
                .translations(List.of(new BrandTranslation("en", "Logitech", "Peripheral manufacturer")))
                .build();
    }

    @Test
    @DisplayName("save persiste y devuelve Brand con id y timestamps")
    void save_persistsAndReturnsBrand() {
        Brand saved = adapter.save(sample);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.translation("en")).isPresent();
        assertThat(saved.translation("en").get().name()).isEqualTo("Logitech");
    }

    @Test
    @DisplayName("findById devuelve la Brand del dominio")
    void findById_returnsDomain() {
        Brand saved = adapter.save(sample);

        Optional<Brand> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().translation("en").get().name()).isEqualTo("Logitech");
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

        assertThat(adapter.existsByLocaleAndNameIgnoreCase("en", "logitech")).isTrue();
    }

    @Test
    @DisplayName("existsByLocaleAndNameIgnoreCase devuelve false cuando no existe")
    void existsByLocaleAndNameIgnoreCase_false() {
        assertThat(adapter.existsByLocaleAndNameIgnoreCase("en", "no-existe")).isFalse();
    }

    @Test
    @DisplayName("existsByLocaleAndNameIgnoreCaseAndIdNot ignora la propia marca")
    void existsByLocaleAndNameIgnoreCaseAndIdNot_excludesSelf() {
        Brand saved = adapter.save(sample);

        assertThat(adapter.existsByLocaleAndNameIgnoreCaseAndIdNot("en", "Logitech", saved.getId())).isFalse();
    }

    @Test
    @DisplayName("existsById detecta id existente")
    void existsById_true() {
        Brand saved = adapter.save(sample);

        assertThat(adapter.existsById(saved.getId())).isTrue();
    }

    @Test
    @DisplayName("findAll devuelve todas las marcas ordenadas por nombre en el idioma por defecto")
    void findAll_returnsAllSortedByName() {
        adapter.save(Brand.builder().translations(List.of(new BrandTranslation("en", "Samsung", null))).build());
        adapter.save(Brand.builder().translations(List.of(new BrandTranslation("en", "Acme", null))).build());
        adapter.save(sample); // Logitech

        List<Brand> result = adapter.findAll();

        assertThat(result).extracting(b -> b.translation("en").get().name())
                .containsExactly("Acme", "Logitech", "Samsung");
    }

    @Test
    @DisplayName("deleteById elimina la marca")
    void deleteById_removes() {
        Brand saved = adapter.save(sample);

        adapter.deleteById(saved.getId());

        assertThat(adapter.findById(saved.getId())).isEmpty();
        assertThat(jpa.count()).isZero();
    }

    @Test
    @DisplayName("save con id existente actualiza la marca")
    void save_updatesExisting() {
        Brand saved = adapter.save(sample);
        Brand modified = saved.withIdAndTimestamps(saved.getId(), saved.getCreatedAt(), saved.getUpdatedAt())
                .toBuilder()
                .translations(List.of(new BrandTranslation("en", "Logitech", "New description")))
                .build();

        Brand updated = adapter.save(modified);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.translation("en").get().description()).isEqualTo("New description");
        assertThat(jpa.count()).isEqualTo(1);
    }
}
