package io.masterdata.common.catalog.domain.service;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogField;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.CatalogTypeDefinition;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.TestCriteria;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
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
 * así que la mayoría de los tests la ejercitan una sola vez usando {@link #REGION} como tipo
 * representativo, más un par de tests que confirman que {@code type} realmente determina el
 * dispatch/contenido de la excepción en lugar de ser ignorado.
 */
@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

    private static final CatalogType REGION = TestCatalogTypes.REGION;

    @Mock
    private CatalogRepository repository;

    @InjectMocks
    private CatalogQueryService service;

    private Catalog sample;

    @BeforeEach
    void setUp() {
        sample = Catalog.builder()
                .id(1L)
                .type(REGION)
                .translations(List.of(new CatalogTranslation("en", "Logitech", "Peripheral manufacturer")))
                .build();
    }

    @Test
    @DisplayName("findById devuelve la entrada cuando existe")
    void findById_found() {
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(sample));

        assertThat(service.findById(REGION, 1L)).isEqualTo(sample);
    }

    @Test
    @DisplayName("findById lanza CatalogNotFoundException con el messageKey del tipo cuando no existe")
    void findById_notFound() {
        when(repository.findById(REGION, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(REGION, 999L))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findById dispatcha con el tipo correcto (PROVINCE vs REGION)")
    void findById_dispatchesByType() {
        when(repository.findById(TestCatalogTypes.PROVINCE, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(TestCatalogTypes.PROVINCE, 999L))
                .isInstanceOf(CatalogNotFoundException.class)
                .hasMessageContaining("Province");

        verify(repository, never()).findById(eq(REGION), any());
    }

    @Test
    @DisplayName("findAll delega en el repositorio con el tipo, el locale, los criterios y el orden")
    void findAll_delegatesToRepository() {
        CatalogSearchCriteria criteria = TestCriteria.of("logi", null, null);
        when(repository.findAll(REGION, "es", criteria, "name", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "es", criteria, "name", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll acepta description como campo de ordenación")
    void findAll_allowsSortingByDescription() {
        CatalogSearchCriteria criteria = TestCriteria.of(null, null, null);
        when(repository.findAll(REGION, "en", criteria, "description", "DESC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "en", criteria, "description", "DESC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll acepta weight como campo de ordenación")
    void findAll_allowsSortingByWeight() {
        CatalogSearchCriteria criteria = TestCriteria.of(null, null, null);
        when(repository.findAll(REGION, "en", criteria, "weight", "DESC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "en", criteria, "weight", "DESC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll lanza InvalidCatalogQueryException cuando sortBy no es ordenable")
    void findAll_throwsWhenSortByNotAllowed() {
        CatalogSearchCriteria criteria = TestCriteria.of(null, null, null);

        assertThatThrownBy(() -> service.findAll(REGION, "en", criteria, "id", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);

        verify(repository, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("findPage delega en el repositorio con el tipo, el locale, los criterios y la paginación")
    void findPage_delegatesToRepository() {
        var criteria = TestCriteria.of("logi", null, null);
        var paging = new Paging(1, 10, "weight", "DESC");
        var page = new PageResult<>(List.of(sample), 1, 10, 11, 2);
        when(repository.findPage(REGION, "es", criteria, paging)).thenReturn(page);

        assertThat(service.findPage(REGION, "es", criteria, paging)).isSameAs(page);
    }

    @Test
    @DisplayName("findPage lanza InvalidCatalogQueryException cuando sortBy no es ordenable, sin consultar el repositorio")
    void findPage_throwsWhenSortByNotAllowed() {
        var criteria = TestCriteria.of(null, null, null);
        when(repository.sortableExtraFields(REGION)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.findPage(REGION, "en", criteria, new Paging(0, 20, "id", "ASC")))
                .isInstanceOf(InvalidCatalogQueryException.class);
        verify(repository, never()).findPage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("findAll acepta un campo adicional del tipo (ineCode) como sortBy cuando el repositorio lo declara ordenable")
    void findAll_allowsSortingByTypeSpecificExtraField() {
        CatalogSearchCriteria criteria = TestCriteria.of(null, null, null);
        when(repository.sortableExtraFields(REGION)).thenReturn(Set.of("ineCode"));
        when(repository.findAll(REGION, "en", criteria, "ineCode", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "en", criteria, "ineCode", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll rechaza un campo adicional que no pertenece a este tipo (regionCode, de province, no es ordenable para region)")
    void findAll_throwsWhenSortByIsExtraFieldOfAnotherType() {
        CatalogSearchCriteria criteria = TestCriteria.of(null, null, null);
        when(repository.sortableExtraFields(REGION)).thenReturn(Set.of("ineCode"));

        assertThatThrownBy(() -> service.findAll(REGION, "en", criteria, "regionCode", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);

        verify(repository, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("findTypeDefinitions devuelve cada tipo configurado con los campos comunes seguidos de sus campos adicionales")
    void findTypeDefinitions_combinesCommonAndExtraFields() {
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("ineCode", "integer"));
        when(repository.extraFieldTypes(TestCatalogTypes.PROVINCE)).thenReturn(Map.of());

        List<CatalogTypeDefinition> result = service.findTypeDefinitions();

        assertThat(result).extracting(CatalogTypeDefinition::type).containsExactly(REGION, TestCatalogTypes.PROVINCE);
        assertThat(result.get(0).fields()).extracting(CatalogField::name)
                .containsExactly("id", "name", "description", "weight", "enabled", "createdAt", "updatedAt", "ineCode");
        assertThat(result.get(0).fields().get(7)).isEqualTo(CatalogField.extra("ineCode", "integer"));
        assertThat(result.get(1).fields()).isEqualTo(CatalogQueryService.COMMON_FIELDS);
    }

    @Test
    @DisplayName("Los campos comunes marcados como ordenables son exactamente ALLOWED_SORT_FIELDS")
    void commonFields_sortableMatchesAllowedSortFields() {
        assertThat(CatalogQueryService.COMMON_FIELDS).filteredOn(CatalogField::sortable).extracting(CatalogField::name)
                .containsExactlyInAnyOrderElementsOf(CatalogQueryService.ALLOWED_SORT_FIELDS);
    }

    private static CatalogSearchCriteria filter(String field, CatalogFilter.Operator operator, String... values) {
        return new CatalogSearchCriteria(List.of(new CatalogFilter(field, operator, List.of(values))));
    }

    @Test
    @DisplayName("findAll acepta un filtro sobre un campo adicional del tipo y delega en el repositorio")
    void findAll_allowsFilteringByExtraField() {
        var criteria = filter("ineCode", CatalogFilter.Operator.EQ, "1");
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("ineCode", "integer"));
        when(repository.findAll(REGION, "en", criteria, "name", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "en", criteria, "name", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll acepta filtros sobre los campos comunes name, description y enabled")
    void findAll_allowsFilteringByCommonFields() {
        var criteria = new CatalogSearchCriteria(List.of(
                new CatalogFilter("name", "logi"), new CatalogFilter("description", "mouse"), new CatalogFilter("enabled", "false")));
        when(repository.findAll(REGION, "en", criteria, "name", "ASC")).thenReturn(List.of(sample));

        assertThat(service.findAll(REGION, "en", criteria, "name", "ASC")).containsExactly(sample);
    }

    @Test
    @DisplayName("findAll rechaza (422) un filtro sobre un campo que no es filtrable en este tipo, listando los válidos")
    void findAll_throwsWhenFilterFieldIsUnknown() {
        var criteria = filter("regionCode", CatalogFilter.Operator.EQ, "1");
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("ineCode", "integer"));

        assertThatThrownBy(() -> service.findAll(REGION, "en", criteria, "name", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("regionCode")
                .hasMessageContaining("[name, description, enabled, ineCode]");
        verify(repository, never()).findAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("findPage rechaza (422) un operador de rango sobre un campo de texto")
    void findPage_throwsWhenRangeOperatorOnTextField() {
        var criteria = filter("isoCode", CatalogFilter.Operator.GTE, "a");
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("isoCode", "string"));

        assertThatThrownBy(() -> service.findPage(REGION, "en", criteria, new Paging(0, 20, "name", "ASC")))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("isoCode.gte");
        verify(repository, never()).findPage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("findAll rechaza (422) un operador de rango sobre name (texto) o enabled (booleano)")
    void findAll_throwsWhenRangeOperatorOnCommonField() {
        assertThatThrownBy(() -> service.findAll(REGION, "en", filter("name", CatalogFilter.Operator.LT, "m"), "name", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> service.findAll(REGION, "en", filter("enabled", CatalogFilter.Operator.GT, "false"), "name", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    @DisplayName("findPage acepta operadores de rango sobre campos numéricos")
    void findPage_allowsRangeOperatorOnNumericField() {
        var criteria = filter("ineCode", CatalogFilter.Operator.LT, "10");
        var paging = new Paging(0, 20, "name", "ASC");
        var page = new PageResult<>(List.of(sample), 0, 20, 1, 1);
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("ineCode", "integer"));
        when(repository.findPage(REGION, "en", criteria, paging)).thenReturn(page);

        assertThat(service.findPage(REGION, "en", criteria, paging)).isSameAs(page);
    }

    @Test
    @DisplayName("Los campos comunes filtrables publican sus operadores: contains para name/description, eq para enabled")
    void commonFieldsPublishFilterOperators() {
        assertThat(CatalogQueryService.COMMON_FIELDS)
                .filteredOn(f -> !f.filterOperators().isEmpty())
                .extracting(CatalogField::name, CatalogField::filterOperators)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("name", List.of("contains")),
                        org.assertj.core.api.Assertions.tuple("description", List.of("contains")),
                        org.assertj.core.api.Assertions.tuple("enabled", List.of("eq")));
    }
}
