package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.port.CatalogMapper;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogTranslationCommand;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogMapperAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de escritura (unicidad del nombre por tipo y locale, no-encontrado en update/delete)
 * de {@link CatalogCommandService}, idénticas para cada {@link CatalogType} — se ejercitan
 * con {@link #BRAND} como tipo representativo.
 */
@ExtendWith(MockitoExtension.class)
class CatalogCommandServiceTest {

    private static final CatalogType BRAND = TestCatalogTypes.BRAND;

    @Mock
    private CatalogRepository repository;

    @Spy
    private CatalogMapper mapper = new CatalogMapperAdapter();

    @InjectMocks
    private CatalogCommandService service;

    private Catalog sample;
    private CreateCatalogCommand validCreate;
    private UpdateCatalogCommand validUpdate;

    @BeforeEach
    void setUp() {
        sample = Catalog.builder()
                .id(1L)
                .type(BRAND)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "Peripheral manufacturer")))
                .build();

        validCreate = new CreateCatalogCommand(BRAND, 0, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "Peripheral manufacturer")));
        validUpdate = new UpdateCatalogCommand(0, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "Peripheral manufacturer")));
    }

    @Test
    @DisplayName("create persiste la entrada cuando el nombre es nuevo en cada idioma")
    void create_persistsWhenNameNew() {
        when(repository.existsByLocaleAndNameIgnoreCase(BRAND, "en", "Logitech")).thenReturn(false);
        when(repository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        Catalog result = service.create(validCreate);

        assertThat(result.translation("en")).isPresent();
        assertThat(result.translation("en").get().name()).isEqualTo("Logitech");
        verify(repository).save(any(Catalog.class));
    }

    @Test
    @DisplayName("create lanza DuplicateCatalogNameException cuando el nombre ya existe en ese idioma")
    void create_throwsWhenNameDuplicated() {
        when(repository.existsByLocaleAndNameIgnoreCase(BRAND, "en", "Logitech")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(DuplicateCatalogNameException.class)
                .hasMessageContaining("Logitech");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update modifica la entrada cuando el nombre no cambia")
    void update_modifiesWhenNameUnchanged() {
        UpdateCatalogCommand updateCmd = new UpdateCatalogCommand(5, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "New description")));
        when(repository.findById(BRAND, 1L)).thenReturn(Optional.of(sample));
        when(repository.existsByLocaleAndNameIgnoreCaseAndIdNot(BRAND, "en", "Logitech", 1L)).thenReturn(false);
        when(repository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        Catalog result = service.update(BRAND, 1L, updateCmd);

        assertThat(result.translation("en").get().description()).isEqualTo("New description");
        assertThat(result.getWeight()).isEqualTo(5);
    }

    @Test
    @DisplayName("update detecta duplicado cuando el nombre cambia a uno existente")
    void update_throwsWhenNewNameExists() {
        UpdateCatalogCommand updateCmd = new UpdateCatalogCommand(0, Map.of(), List.of(new CatalogTranslationCommand("en", "Samsung", "desc")));
        when(repository.findById(BRAND, 1L)).thenReturn(Optional.of(sample));
        when(repository.existsByLocaleAndNameIgnoreCaseAndIdNot(BRAND, "en", "Samsung", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(BRAND, 1L, updateCmd))
                .isInstanceOf(DuplicateCatalogNameException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update lanza CatalogNotFoundException cuando el id no existe")
    void update_throwsWhenIdNotFound() {
        when(repository.findById(BRAND, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(BRAND, 999L, validUpdate))
                .isInstanceOf(CatalogNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete borra cuando el id existe")
    void delete_removesWhenExists() {
        when(repository.existsById(BRAND, 1L)).thenReturn(true);

        service.delete(BRAND, 1L);

        verify(repository).deleteById(BRAND, 1L);
    }

    @Test
    @DisplayName("delete lanza CatalogNotFoundException cuando el id no existe")
    void delete_throwsWhenNotFound() {
        when(repository.existsById(BRAND, 999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(BRAND, 999L))
                .isInstanceOf(CatalogNotFoundException.class);

        verify(repository, never()).deleteById(any(), any());
    }
}
