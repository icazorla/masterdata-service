package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogMapper;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La lógica de servicio bajo prueba (comprobaciones de unicidad de traducciones,
 * gestión de no-encontrado/duplicado) es idéntica para cada {@link CatalogType} — ver
 * {@link CatalogService} — así que la mayoría de los tests la ejercitan una sola vez
 * usando {@link #BRAND} como tipo representativo, más un par de tests que confirman
 * que {@code type} realmente determina el dispatch/contenido de la excepción en lugar
 * de ser ignorado.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    private static final CatalogType BRAND = CatalogType.BRAND;

    @Mock
    private CatalogRepository repository;

    @Spy
    private CatalogMapper mapper = new CatalogMapperAdapter();

    @InjectMocks
    private CatalogService service;

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
    @DisplayName("findById devuelve la entrada cuando existe")
    void findById_found() {
        when(repository.findById(BRAND, 1L)).thenReturn(Optional.of(sample));

        assertThat(service.findById(BRAND, 1L)).isEqualTo(sample);
    }

    @Test
    @DisplayName("findById lanza CatalogNotFoundException con el messageKey del tipo cuando no existe")
    void findById_notFound() {
        when(repository.findById(BRAND, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(BRAND, 999L))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findById dispatcha con el tipo correcto (CATEGORY vs BRAND)")
    void findById_dispatchesByType() {
        when(repository.findById(CatalogType.CATEGORY, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(CatalogType.CATEGORY, 999L))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("Category");

        verify(repository, never()).findById(eq(BRAND), any());
    }

    @Test
    @DisplayName("findAll delega en el repositorio con el tipo, el locale, los criterios y el orden")
    void findAll_delegatesToRepository() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria("logi", null);
        when(repository.findAll(BRAND, "es", criteria, "name", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(BRAND, "es", criteria, "name", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll acepta description como campo de ordenación")
    void findAll_allowsSortingByDescription() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(null, null);
        when(repository.findAll(BRAND, "en", criteria, "description", "DESC")).thenReturn(List.of(sample));

        assertThat(service.findAll(BRAND, "en", criteria, "description", "DESC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll acepta weight como campo de ordenación")
    void findAll_allowsSortingByWeight() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(null, null);
        when(repository.findAll(BRAND, "en", criteria, "weight", "DESC")).thenReturn(List.of(sample));

        assertThat(service.findAll(BRAND, "en", criteria, "weight", "DESC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll lanza InvalidCatalogQueryException cuando sortBy no es ordenable")
    void findAll_throwsWhenSortByNotAllowed() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(null, null);

        assertThatThrownBy(() -> service.findAll(BRAND, "en", criteria, "id", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);

        verify(repository, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("findAll acepta un campo adicional del tipo (website) como sortBy cuando el repositorio lo declara ordenable")
    void findAll_allowsSortingByTypeSpecificExtraField() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(null, null);
        when(repository.sortableExtraFields(BRAND)).thenReturn(Set.of("website"));
        when(repository.findAll(BRAND, "en", criteria, "website", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(BRAND, "en", criteria, "website", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll rechaza un campo adicional que no pertenece a este tipo (website no es ordenable para category)")
    void findAll_throwsWhenSortByIsExtraFieldOfAnotherType() {
        CatalogSearchCriteria criteria = new CatalogSearchCriteria(null, null);
        when(repository.sortableExtraFields(CatalogType.CATEGORY)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.findAll(CatalogType.CATEGORY, "en", criteria, "website", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);

        verify(repository, never()).findAll(any(), any(), any(), any(), any());
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

    @Test
    @DisplayName("existsById delega en el repositorio con el tipo")
    void existsById_delegatesToRepository() {
        when(repository.existsById(BRAND, 1L)).thenReturn(true);

        assertThat(service.existsById(BRAND, 1L)).isTrue();
    }
}
