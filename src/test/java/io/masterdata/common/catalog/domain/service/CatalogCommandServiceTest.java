package io.masterdata.common.catalog.domain.service;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.exception.CatalogVersionConflictException;
import io.masterdata.common.catalog.domain.exception.DuplicateCatalogNameException;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.port.CatalogTranslationCommand;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de escritura (unicidad del nombre por tipo y locale, no-encontrado en update/delete)
 * de {@link CatalogCommandService}, idénticas para cada {@link CatalogType} — se ejercitan
 * con {@link #REGION} como tipo representativo.
 */
@ExtendWith(MockitoExtension.class)
class CatalogCommandServiceTest {

    private static final CatalogType REGION = TestCatalogTypes.REGION;

    @Mock
    private CatalogRepository repository;

    @InjectMocks
    private CatalogCommandService service;

    private Catalog sample;
    private CreateCatalogCommand validCreate;
    private UpdateCatalogCommand validUpdate;

    @BeforeEach
    void setUp() {
        sample = Catalog.builder()
                .id(1L)
                .type(REGION)
                .version(3L)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "Peripheral manufacturer")))
                .build();

        validCreate = new CreateCatalogCommand(REGION, 0, true, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "Peripheral manufacturer")));
        validUpdate = new UpdateCatalogCommand(null, 0, true, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "Peripheral manufacturer")));
    }

    @Test
    @DisplayName("create persiste la entrada cuando el nombre es nuevo en cada idioma")
    void create_persistsWhenNameNew() {
        when(repository.existsByLocaleAndName(REGION, "en", "Logitech", null)).thenReturn(false);
        when(repository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        Catalog result = service.create(validCreate);

        assertThat(result.translation("en")).isPresent();
        assertThat(result.translation("en").get().name()).isEqualTo("Logitech");
        verify(repository).save(any(Catalog.class));
    }

    @Test
    @DisplayName("create lanza DuplicateCatalogNameException cuando el nombre ya existe en ese idioma")
    void create_throwsWhenNameDuplicated() {
        when(repository.existsByLocaleAndName(REGION, "en", "Logitech", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(validCreate))
                .isInstanceOf(DuplicateCatalogNameException.class)
                .hasMessageContaining("Logitech");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update modifica la entrada cuando el nombre no cambia")
    void update_modifiesWhenNameUnchanged() {
        UpdateCatalogCommand updateCmd = new UpdateCatalogCommand(null, 5, true, Map.of(), List.of(new CatalogTranslationCommand("en", "Logitech", "New description")));
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));
        when(repository.existsByLocaleAndName(REGION, "en", "Logitech", 1L)).thenReturn(false);
        when(repository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        Catalog result = service.update(REGION, 1L, updateCmd);

        assertThat(result.translation("en").get().description()).isEqualTo("New description");
        assertThat(result.getWeight()).isEqualTo(5);
    }

    @Test
    @DisplayName("update detecta duplicado cuando el nombre cambia a uno existente")
    void update_throwsWhenNewNameExists() {
        UpdateCatalogCommand updateCmd = new UpdateCatalogCommand(null, 0, true, Map.of(), List.of(new CatalogTranslationCommand("en", "Samsung", "desc")));
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));
        when(repository.existsByLocaleAndName(REGION, "en", "Samsung", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(REGION, 1L, updateCmd))
                .isInstanceOf(DuplicateCatalogNameException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update rechaza los cambios hechos sobre una versión que ya no es la actual, sin guardar")
    void update_throwsWhenVersionIsStale() {
        UpdateCatalogCommand stale = new UpdateCatalogCommand(2L, 0, true, Map.of(), validUpdate.translations());
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> service.update(REGION, 1L, stale))
                .isInstanceOf(CatalogVersionConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update guarda cuando la versión esperada es la actual")
    void update_savesWhenVersionMatches() {
        UpdateCatalogCommand current = new UpdateCatalogCommand(3L, 0, true, Map.of(), validUpdate.translations());
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));
        when(repository.save(any(Catalog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(REGION, 1L, current);

        verify(repository).save(any(Catalog.class));
    }

    @Test
    @DisplayName("delete rechaza borrar sobre una versión que ya no es la actual")
    void delete_throwsWhenVersionIsStale() {
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> service.delete(REGION, 1L, 2L))
                .isInstanceOf(CatalogVersionConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update lanza CatalogNotFoundException cuando el id no existe")
    void update_throwsWhenIdNotFound() {
        when(repository.findById(REGION, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(REGION, 999L, validUpdate))
                .isInstanceOf(CatalogNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete es lógico: guarda la entrada con enabled=false, sin tocar el resto")
    void delete_disablesWhenExists() {
        Catalog existing = Catalog.builder().id(1L).type(REGION).weight(5)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(existing));

        service.delete(REGION, 1L, null);

        verify(repository).save(argThat(c -> c.getId().equals(1L) && !c.isEnabled() && c.getWeight() == 5
                && c.getTranslations().equals(existing.getTranslations())));
    }

    @Test
    @DisplayName("delete de una entrada ya desactivada no escribe nada (idempotente)")
    void delete_isNoOpWhenAlreadyDisabled() {
        Catalog disabled = Catalog.builder().id(1L).type(REGION).enabled(false)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(disabled));

        service.delete(REGION, 1L, null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete lanza CatalogNotFoundException cuando el id no existe")
    void delete_throwsWhenNotFound() {
        when(repository.findById(REGION, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(REGION, 999L, null))
                .isInstanceOf(CatalogNotFoundException.class);

        verify(repository, never()).save(any());
    }
}
