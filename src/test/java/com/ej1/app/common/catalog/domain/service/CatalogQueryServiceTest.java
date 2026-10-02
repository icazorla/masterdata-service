package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogField;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.CatalogTypeDefinition;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La lógica de lectura bajo prueba (validación de {@code sortBy}, no-encontrado, definición
 * de tipos) es idéntica para cada {@link CatalogType} — ver {@link CatalogQueryService} —
 * así que la mayoría de los tests la ejercitan una sola vez usando {@link #BRAND} como tipo
 * representativo, más un par de tests que confirman que {@code type} realmente determina el
 * dispatch/contenido de la excepción en lugar de ser ignorado.
 */
@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

    private static final CatalogType BRAND = TestCatalogTypes.BRAND;

    @Mock
    private CatalogRepository repository;

    @InjectMocks
    private CatalogQueryService service;

    private Catalog sample;

    @BeforeEach
    void setUp() {
        sample = Catalog.builder()
                .id(1L)
                .type(BRAND)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "Peripheral manufacturer")))
                .build();
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
        when(repository.findById(TestCatalogTypes.CATEGORY, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(TestCatalogTypes.CATEGORY, 999L))
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
    @DisplayName("findPage delega en el repositorio con el tipo, el locale, los criterios y la paginación")
    void findPage_delegatesToRepository() {
        var criteria = new CatalogSearchCriteria("logi", null);
        var paging = new Paging(1, 10, "weight", "DESC");
        var page = new PageResult<>(List.of(sample), 1, 10, 11, 2);
        when(repository.findPage(BRAND, "es", criteria, paging)).thenReturn(page);

        assertThat(service.findPage(BRAND, "es", criteria, paging)).isSameAs(page);
    }

    @Test
    @DisplayName("findPage lanza InvalidCatalogQueryException cuando sortBy no es ordenable, sin consultar el repositorio")
    void findPage_throwsWhenSortByNotAllowed() {
        var criteria = new CatalogSearchCriteria(null, null);
        when(repository.sortableExtraFields(BRAND)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.findPage(BRAND, "en", criteria, new Paging(0, 20, "id", "ASC")))
                .isInstanceOf(InvalidCatalogQueryException.class);
        verify(repository, never()).findPage(any(), any(), any(), any());
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
        when(repository.sortableExtraFields(TestCatalogTypes.CATEGORY)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.findAll(TestCatalogTypes.CATEGORY, "en", criteria, "website", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);

        verify(repository, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("findTypeDefinitions devuelve cada tipo configurado con los campos comunes seguidos de sus campos adicionales")
    void findTypeDefinitions_combinesCommonAndExtraFields() {
        when(repository.extraFieldTypes(BRAND)).thenReturn(Map.of("website", "string"));
        when(repository.extraFieldTypes(TestCatalogTypes.CATEGORY)).thenReturn(Map.of());

        List<CatalogTypeDefinition> result = service.findTypeDefinitions();

        assertThat(result).extracting(CatalogTypeDefinition::type).containsExactly(BRAND, TestCatalogTypes.CATEGORY);
        assertThat(result.get(0).fields()).extracting(CatalogField::name)
                .containsExactly("id", "name", "description", "weight", "createdAt", "updatedAt", "website");
        assertThat(result.get(0).fields().get(6)).isEqualTo(CatalogField.extra("website", "string"));
        assertThat(result.get(1).fields()).isEqualTo(CatalogQueryService.COMMON_FIELDS);
    }

    @Test
    @DisplayName("Los campos comunes marcados como ordenables son exactamente ALLOWED_SORT_FIELDS")
    void commonFields_sortableMatchesAllowedSortFields() {
        assertThat(CatalogQueryService.COMMON_FIELDS).filteredOn(CatalogField::sortable).extracting(CatalogField::name)
                .containsExactlyInAnyOrderElementsOf(CatalogQueryService.ALLOWED_SORT_FIELDS);
    }

    @Test
    @DisplayName("existsById delega en el repositorio con el tipo")
    void existsById_delegatesToRepository() {
        when(repository.existsById(BRAND, 1L)).thenReturn(true);

        assertThat(service.existsById(BRAND, 1L)).isTrue();
    }
}
