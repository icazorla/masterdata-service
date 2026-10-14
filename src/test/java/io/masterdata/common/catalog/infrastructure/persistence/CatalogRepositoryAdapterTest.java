package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.catalog.ProvinceEntity;
import io.masterdata.catalog.RegionEntity;
import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.TestCriteria;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link CatalogRepositoryAdapter} es una única clase que sirve a todos los
 * {@link CatalogType} — así que, a diferencia de los antiguos tests de adaptador por
 * agregado, esta es una sola clase de test, parametrizada sobre cada
 * {@link CatalogType} configurado en lugar de duplicada por tipo. {@link
 * #catalogTypes()} es un {@code @MethodSource}, no un {@code @EnumSource},
 * precisamente porque {@link CatalogType} ya no es un enum — {@code app.catalog.types}
 * en {@code application.yml} es la lista maestra (ver {@code CatalogPersistenceConfig}).
 * Cada método de test se ejecuta una vez por tipo y obtiene una transacción vacía que
 * se revierte al finalizar gracias a {@code @DataJpaTest} — sin necesidad de limpieza
 * manual entre ejecuciones.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({CatalogRepositoryAdapter.class, CatalogPersistenceConfig.class})
@TestPropertySource(properties = "spring.web.locale=en")
class CatalogRepositoryAdapterTest {

    static Stream<CatalogType> catalogTypes() {
        return Stream.of(TestCatalogTypes.REGION, TestCatalogTypes.PROVINCE);
    }

    @Autowired
    private CatalogRepositoryAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private Catalog sample(CatalogType type, String name, String description) {
        return Catalog.builder()
                .type(type)
                .translations(List.of(new CatalogTranslation("en", name, description)))
                .build();
    }

    private Catalog sample(CatalogType type, CatalogTranslation... translations) {
        return Catalog.builder().type(type).translations(List.of(translations)).build();
    }

    private Catalog sampleWithWeight(CatalogType type, String name, int weight) {
        return Catalog.builder().type(type).weight(weight)
                .translations(List.of(new CatalogTranslation("en", name, null)))
                .build();
    }

    private static final CatalogSearchCriteria NO_FILTER = TestCriteria.of(null, null, null);

    @ParameterizedTest(name = "[{0}] save persiste y devuelve el Catalog con id y timestamps")
    @MethodSource("catalogTypes")
    void save_persistsAndReturnsCatalog(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.translation("en")).isPresent();
        assertThat(saved.translation("en").get().name()).isEqualTo("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findById devuelve el Catalog del dominio")
    @MethodSource("catalogTypes")
    void findById_returnsDomain(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        Optional<Catalog> result = adapter.findById(type, saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().translation("en").get().name()).isEqualTo("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findById devuelve Optional.empty cuando no existe")
    @MethodSource("catalogTypes")
    void findById_empty(CatalogType type) {
        assertThat(adapter.findById(type, 999L)).isEmpty();
    }

    @ParameterizedTest(name = "[{0}] existsByLocaleAndNameIgnoreCase detecta nombre existente sin distinguir mayúsculas")
    @MethodSource("catalogTypes")
    void existsByLocaleAndNameIgnoreCase_true(CatalogType type) {
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        assertThat(adapter.existsByLocaleAndNameIgnoreCase(type, "en", "logitech")).isTrue();
    }

    @ParameterizedTest(name = "[{0}] existsByLocaleAndNameIgnoreCase devuelve false cuando no existe")
    @MethodSource("catalogTypes")
    void existsByLocaleAndNameIgnoreCase_false(CatalogType type) {
        assertThat(adapter.existsByLocaleAndNameIgnoreCase(type, "en", "no-existe")).isFalse();
    }

    @ParameterizedTest(name = "[{0}] existsByLocaleAndNameIgnoreCaseAndIdNot ignora la propia entrada")
    @MethodSource("catalogTypes")
    void existsByLocaleAndNameIgnoreCaseAndIdNot_excludesSelf(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        assertThat(adapter.existsByLocaleAndNameIgnoreCaseAndIdNot(type, "en", "Logitech", saved.getId())).isFalse();
    }

    @ParameterizedTest(name = "[{0}] existsById detecta id existente")
    @MethodSource("catalogTypes")
    void existsById_true(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        assertThat(adapter.existsById(type, saved.getId())).isTrue();
    }

    @ParameterizedTest(name = "[{0}] existsById devuelve false cuando no existe")
    @MethodSource("catalogTypes")
    void existsById_false(CatalogType type) {
        assertThat(adapter.existsById(type, 999L)).isFalse();
    }

    @ParameterizedTest(name = "[{0}] findAll devuelve todas las entradas ordenadas por nombre")
    @MethodSource("catalogTypes")
    void findAll_returnsAllSortedByName(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));
        adapter.save(sample(type, "Acme", null));
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Logitech", "Samsung");
    }

    @ParameterizedTest(name = "[{0}] findAll ordena por nombre descendente")
    @MethodSource("catalogTypes")
    void findAll_sortsByNameDescending(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));
        adapter.save(sample(type, "Acme", null));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "name", "DESC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Samsung", "Acme");
    }

    @ParameterizedTest(name = "[{0}] findAll ordena por descripción")
    @MethodSource("catalogTypes")
    void findAll_sortsByDescription(CatalogType type) {
        adapter.save(sample(type, "Samsung", "Zeta"));
        adapter.save(sample(type, "Acme", "Alpha"));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "description", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Samsung");
    }

    @ParameterizedTest(name = "[{0}] findPage devuelve la página pedida, ordenada, con totales")
    @MethodSource("catalogTypes")
    void findPage_returnsRequestedSliceWithTotals(CatalogType type) {
        for (String name : List.of("Echo", "Acme", "Delta", "Bravo", "Charlie")) {
            adapter.save(sample(type, name, null));
        }

        PageResult<Catalog> first = adapter.findPage(type, "en", NO_FILTER, new Paging(0, 2, "name", "ASC"));
        PageResult<Catalog> last = adapter.findPage(type, "en", NO_FILTER, new Paging(2, 2, "name", "ASC"));

        assertThat(first.content()).extracting(c -> c.translation("en").get().name()).containsExactly("Acme", "Bravo");
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(last.content()).extracting(c -> c.translation("en").get().name()).containsExactly("Echo");
        assertThat(last.totalElements()).isEqualTo(5);
        assertThat(last.totalPages()).isEqualTo(3);
    }

    @ParameterizedTest(name = "[{0}] findPage aplica el filtro tanto al contenido como al total")
    @MethodSource("catalogTypes")
    void findPage_appliesFilterToContentAndTotal(CatalogType type) {
        adapter.save(sample(type, "Logitech", null));
        adapter.save(sample(type, "Logi Pro", null));
        adapter.save(sample(type, "Samsung", null));

        PageResult<Catalog> result = adapter.findPage(type, "en", TestCriteria.of("logi", null, null),
                new Paging(0, 1, "name", "DESC"));

        assertThat(result.content()).extracting(c -> c.translation("en").get().name()).containsExactly("Logitech");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @ParameterizedTest(name = "[{0}] findPage fuera de rango devuelve contenido vacío pero el total real")
    @MethodSource("catalogTypes")
    void findPage_outOfRangeReturnsEmptyContentWithTotal(CatalogType type) {
        adapter.save(sample(type, "Acme", null));
        adapter.save(sample(type, "Bravo", null));

        PageResult<Catalog> result = adapter.findPage(type, "en", NO_FILTER, new Paging(5, 2, "name", "ASC"));
        PageResult<Catalog> huge = adapter.findPage(type, "en", NO_FILTER, new Paging(Integer.MAX_VALUE, 100, "name", "ASC"));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(huge.content()).isEmpty();
        assertThat(huge.totalElements()).isEqualTo(2);
    }

    @ParameterizedTest(name = "[{0}] findPage desempata por id: entradas con el mismo peso no se repiten ni se pierden entre páginas")
    @MethodSource("catalogTypes")
    void findPage_tieBreaksById(CatalogType type) {
        List<Long> ids = new ArrayList<>();
        for (String name : List.of("A", "B", "C", "D")) {
            ids.add(adapter.save(sampleWithWeight(type, name, 7)).getId());
        }

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            adapter.findPage(type, "en", NO_FILTER, new Paging(page, 2, "weight", "ASC"))
                    .content().forEach(c -> seen.add(c.getId()));
        }

        assertThat(seen).containsExactlyElementsOf(ids);
    }

    @ParameterizedTest(name = "[{0}] save persiste el peso por defecto (0) cuando no se indica")
    @MethodSource("catalogTypes")
    void save_defaultsWeightToZero(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        assertThat(saved.getWeight()).isZero();
    }

    @ParameterizedTest(name = "[{0}] save persiste y devuelve el peso indicado")
    @MethodSource("catalogTypes")
    void save_persistsWeight(CatalogType type) {
        Catalog saved = adapter.save(sampleWithWeight(type, "Logitech", 42));

        assertThat(saved.getWeight()).isEqualTo(42);
    }

    @ParameterizedTest(name = "[{0}] save con id existente actualiza el peso")
    @MethodSource("catalogTypes")
    void save_updatesWeight(CatalogType type) {
        Catalog saved = adapter.save(sampleWithWeight(type, "Logitech", 1));

        Catalog updated = adapter.save(saved.toBuilder().weight(99).build());

        assertThat(updated.getWeight()).isEqualTo(99);
        assertThat(adapter.findById(type, saved.getId()).get().getWeight()).isEqualTo(99);
    }

    @ParameterizedTest(name = "[{0}] findAll ordena por peso, no traducido (mismo orden en cualquier locale)")
    @MethodSource("catalogTypes")
    void findAll_sortsByWeight(CatalogType type) {
        adapter.save(sampleWithWeight(type, "Samsung", 5));
        adapter.save(sampleWithWeight(type, "Acme", 20));
        adapter.save(sampleWithWeight(type, "Logitech", 10));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "weight", "DESC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Logitech", "Samsung");
    }

    @ParameterizedTest(name = "[{0}] findAll respeta ASC explícito cuando sortBy=weight (caso excepción, no fuerza DESC)")
    @MethodSource("catalogTypes")
    void findAll_sortsByWeightAscendingWhenExplicitlyRequested(CatalogType type) {
        adapter.save(sampleWithWeight(type, "Samsung", 5));
        adapter.save(sampleWithWeight(type, "Acme", 20));
        adapter.save(sampleWithWeight(type, "Logitech", 10));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "weight", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Samsung", "Logitech", "Acme");
    }

    @ParameterizedTest(name = "[{0}] findAll ordena siempre por weight DESC primero, y solo dentro de cada peso aplica sortBy/sortDirection")
    @MethodSource("catalogTypes")
    void findAll_appliesWeightDescAlwaysBeforeSortBy(CatalogType type) {
        adapter.save(sampleWithWeight(type, "Zeta", 10));
        adapter.save(sampleWithWeight(type, "Alpha", 5));
        adapter.save(sampleWithWeight(type, "Beta", 10));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "name", "ASC");

        // weight=10 (Beta, Zeta, ordenadas por name ASC entre sí) siempre antes que weight=5 (Alpha),
        // aunque "Alpha" sea alfabéticamente la primera — weight manda, sortBy solo desempata.
        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Beta", "Zeta", "Alpha");
    }

    @ParameterizedTest(name = "[{0}] findAll aplica weight DESC primero también cuando sortDirection=DESC sobre el campo secundario")
    @MethodSource("catalogTypes")
    void findAll_appliesWeightDescAlwaysBeforeSortByDescending(CatalogType type) {
        adapter.save(sampleWithWeight(type, "Zeta", 10));
        adapter.save(sampleWithWeight(type, "Alpha", 5));
        adapter.save(sampleWithWeight(type, "Beta", 10));

        List<Catalog> result = adapter.findAll(type, "en", NO_FILTER, "name", "DESC");

        // Mismo grupo de weight=10 primero (Zeta, Beta en orden name DESC), luego weight=5 (Alpha) —
        // sortDirection=DESC solo invierte el desempate dentro de cada peso, no el orden de weight en sí.
        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Zeta", "Beta", "Alpha");
    }

    private Catalog region(String name, int ineCode) {
        return adapter.save(Catalog.builder().type(TestCatalogTypes.REGION)
                .extraFields(Map.of("ineCode", ineCode))
                .translations(List.of(new CatalogTranslation("en", name, null)))
                .build());
    }

    @Test
    void save_persistsAndReturnsRegionOnlyExtraField() {
        Catalog saved = region("Andalusia", 1);

        assertThat(saved.getExtraFields()).containsEntry("ineCode", 1);
        assertThat(adapter.findById(TestCatalogTypes.REGION, saved.getId()).get().getExtraFields())
                .containsEntry("ineCode", 1);
    }

    @Test
    void save_updatesRegionOnlyExtraField() {
        Catalog saved = region("Andalusia", 1);

        Catalog updated = adapter.save(saved.toBuilder()
                .extraFields(Map.of("ineCode", 2))
                .build());

        assertThat(updated.getExtraFields()).containsEntry("ineCode", 2);
    }

    @Test
    void findById_regionHasOnlyItsOwnExtraFields() {
        Catalog saved = adapter.save(sample(TestCatalogTypes.REGION, "Andalusia", null));

        Catalog result = adapter.findById(TestCatalogTypes.REGION, saved.getId()).get();

        assertThat(result.getExtraFields()).containsOnlyKeys("ineCode");
    }

    @Test
    void sortableExtraFields_returnsEachTypeOwnColumns() {
        assertThat(adapter.sortableExtraFields(TestCatalogTypes.REGION)).containsExactly("ineCode");
        assertThat(adapter.sortableExtraFields(TestCatalogTypes.PROVINCE)).containsExactlyInAnyOrder("ineCode", "regionCode");
    }

    @Test
    @DisplayName("discoverExtraFields toma solo las columnas declaradas en la entidad: ni las heredadas de "
            + "CatalogBaseEntity (id, weight, enabled, timestamps) ni la relación translations")
    void discoverExtraFields_onlyBasicColumnsDeclaredOnEntity() {
        Metamodel metamodel = entityManager.getMetamodel();

        assertThat(CatalogPersistenceConfig.discoverExtraFields(metamodel, RegionEntity.class)).containsExactly("ineCode");
        assertThat(CatalogPersistenceConfig.discoverExtraFields(metamodel, ProvinceEntity.class))
                .containsExactly("ineCode", "regionCode");
    }

    @Test
    @DisplayName("findAll ordena region por ineCode (campo adicional del tipo, no traducido), sin coalesce ni join extra")
    void findAll_sortsRegionByExtraField() {
        region("Samsung", 2);
        region("Acme", 1);

        List<Catalog> result = adapter.findAll(TestCatalogTypes.REGION, "en", NO_FILTER, "ineCode", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Samsung");
    }

    @Test
    @DisplayName("findAll rechaza sortBy=regionCode para region: no es una columna de su entidad")
    void findAll_rejectsExtraFieldNotConfiguredForType() {
        adapter.save(sample(TestCatalogTypes.REGION, "Andalusia", null));

        assertThatThrownBy(() -> adapter.findAll(TestCatalogTypes.REGION, "en", NO_FILTER, "regionCode", "ASC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}] findAll filtra por nombre (subcadena, sin distinguir mayúsculas)")
    @MethodSource("catalogTypes")
    void findAll_filtersByName(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "en", TestCriteria.of("logi", null, null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll filtra por descripción (subcadena, sin distinguir mayúsculas)")
    @MethodSource("catalogTypes")
    void findAll_filtersByDescription(CatalogType type) {
        adapter.save(sample(type, "Samsung", "Electronics maker"));
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "en", TestCriteria.of(null, "peripheral", null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll combina name y description con AND, como cualquier otro filtro")
    @MethodSource("catalogTypes")
    void findAll_combinesNameAndDescriptionWithAnd(CatalogType type) {
        adapter.save(sample(type, "Logitech", "Bluetooth mouse maker")); // coincide por ambos
        adapter.save(sample(type, "Logi Clone", "Peripheral manufacturer")); // solo por name
        adapter.save(sample(type, "Acme", "Bluetooth mouse maker")); // solo por description

        List<Catalog> result = adapter.findAll(type, "en", TestCriteria.of("logi", "mouse", null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name()).containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] varios valores en name significan contiene alguno (OR)")
    @MethodSource("catalogTypes")
    void findAll_combinesNameValuesWithOr(CatalogType type) {
        adapter.save(sample(type, "Logitech", null));
        adapter.save(sample(type, "Samsung", null));
        adapter.save(sample(type, "Acme", null));

        var criteria = new CatalogSearchCriteria(List.of(new CatalogFilter("name", "logi", "SAMS")));

        assertThat(adapter.findAll(type, "en", criteria, "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Logitech", "Samsung");
    }

    @ParameterizedTest(name = "[{0}] findAll devuelve vacío cuando ningún nombre coincide con el filtro")
    @MethodSource("catalogTypes")
    void findAll_returnsEmptyWhenNameFilterMatchesNothing(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));

        List<Catalog> result = adapter.findAll(type, "en", TestCriteria.of("no-existe", null, null), "name", "ASC");

        assertThat(result).isEmpty();
    }

    // En inglés el orden alfabético de description sería Logitech > Acme > Samsung; en español
    // es justo el opuesto. Si el orden se aplicara sobre el inglés en vez de sobre el español
    // pedido, este test fallaría.
    @ParameterizedTest(name = "[{0}] findAll ordena y filtra sobre la traducción del locale pedido, no sobre el idioma por defecto")
    @MethodSource("catalogTypes")
    void findAll_sortsAndFiltersOnRequestedLocaleNotDefaultLocale(CatalogType type) {
        adapter.save(sample(type,
                new CatalogTranslation("en", "Logitech", "Peripheral manufacturer"),
                new CatalogTranslation("es", "Logitech", "Fabricante de periféricos")));
        adapter.save(sample(type,
                new CatalogTranslation("en", "Acme", "Generic brand for everything"),
                new CatalogTranslation("es", "Acme", "Marca genérica para todo")));
        adapter.save(sample(type,
                new CatalogTranslation("en", "Samsung", "Electronics manufacturer"),
                new CatalogTranslation("es", "Samsung", "Fabricante de electrónica")));

        List<Catalog> result = adapter.findAll(type, "es", NO_FILTER, "description", "DESC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Logitech", "Samsung");
    }

    @ParameterizedTest(name = "[{0}] findAll filtra sobre la traducción del locale pedido")
    @MethodSource("catalogTypes")
    void findAll_filtersOnRequestedLocale(CatalogType type) {
        adapter.save(sample(type,
                new CatalogTranslation("en", "Logitech", "Peripheral manufacturer"),
                new CatalogTranslation("es", "Logitech", "Fabricante de periféricos")));
        adapter.save(sample(type,
                new CatalogTranslation("en", "Samsung", "Electronics manufacturer"),
                new CatalogTranslation("es", "Samsung", "Fabricante de electrónica")));

        List<Catalog> result = adapter.findAll(type, "es", TestCriteria.of(null, "periféricos", null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll recurre al idioma por defecto cuando la entrada no tiene traducción para el locale pedido")
    @MethodSource("catalogTypes")
    void findAll_fallsBackToDefaultLocaleWhenEntryHasNoTranslationForRequestedLocale(CatalogType type) {
        // Solo tiene traducción en inglés (el idioma por defecto en este test) — filtrar/ordenar
        // en "es" debe seguir encontrándola gracias al fallback, igual que Catalog#resolveTranslation.
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "es", TestCriteria.of("logi", null, null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] save con id existente actualiza la entrada")
    @MethodSource("catalogTypes")
    void save_updatesExisting(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));
        Catalog modified = saved.toBuilder()
                .translations(List.of(new CatalogTranslation("en", "Logitech", "New description")))
                .build();

        Catalog updated = adapter.save(modified);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.translation("en").get().description()).isEqualTo("New description");
    }

    @ParameterizedTest(name = "[{0}] save sustituye las traducciones en base de datos, reutilizando el mismo (locale, name) sin violar su unicidad")
    @MethodSource("catalogTypes")
    void save_replacesTranslationsInDatabase(CatalogType type) {
        Catalog saved = adapter.save(sample(type,
                new CatalogTranslation("en", "Logitech", "Old"),
                new CatalogTranslation("es", "Logitech ES", null)));
        entityManager.flush();

        adapter.save(saved.toBuilder()
                .translations(List.of(
                        new CatalogTranslation("en", "Logitech", "New"),
                        new CatalogTranslation("fr", "Logitech FR", null)))
                .build());
        entityManager.flush();
        entityManager.clear();

        Catalog reloaded = adapter.findById(type, saved.getId()).get();
        assertThat(reloaded.getTranslations())
                .extracting(CatalogTranslation::locale, CatalogTranslation::name, CatalogTranslation::description)
                .containsExactlyInAnyOrder(
                        tuple("en", "Logitech", "New"),
                        tuple("fr", "Logitech FR", null));
    }

    @ParameterizedTest(name = "[{0}] save persiste enabled=true por defecto")
    @MethodSource("catalogTypes")
    void save_enabledByDefault(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", null));

        assertThat(adapter.findById(type, saved.getId()).get().isEnabled()).isTrue();
    }

    @ParameterizedTest(name = "[{0}] save con id existente actualiza enabled")
    @MethodSource("catalogTypes")
    void save_updatesEnabled(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", null));

        Catalog updated = adapter.save(saved.toBuilder().enabled(false).build());

        assertThat(updated.isEnabled()).isFalse();
        assertThat(adapter.findById(type, saved.getId()).get().isEnabled()).isFalse();
    }

    @ParameterizedTest(name = "[{0}] findAll/findPage filtran por enabled, y sin filtro devuelven todas")
    @MethodSource("catalogTypes")
    void findAll_filtersByEnabled(CatalogType type) {
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));
        adapter.save(sample(type, "Samsung", null).toBuilder().enabled(false).build());

        assertThat(adapter.findAll(type, "en", TestCriteria.of(null, null, true), "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Logitech");
        assertThat(adapter.findAll(type, "en", TestCriteria.of(null, null, false), "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Samsung");
        assertThat(adapter.findAll(type, "en", NO_FILTER, "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Logitech", "Samsung");

        PageResult<Catalog> page = adapter.findPage(type, "en", TestCriteria.of(null, null, true),
                new Paging(0, 1, "name", "ASC"));
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{0}] el filtro enabled se combina con AND con el de texto")
    @MethodSource("catalogTypes")
    void findAll_combinesEnabledWithTextFilterUsingAnd(CatalogType type) {
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));
        adapter.save(sample(type, "Logi Old", null).toBuilder().enabled(false).build());

        List<Catalog> result = adapter.findAll(type, "en", TestCriteria.of("logi", null, true), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name()).containsExactly("Logitech");
    }

    @Test
    @DisplayName("Los filtros de campo adicional se combinan con AND con el de name y aplican también al total paginado")
    void findPage_combinesExtraFieldWithNameUsingAnd() {
        province("Almería", 4, 1);
        province("Albacete", 2, 8);
        province("Cádiz", 11, 1);

        var criteria = new CatalogSearchCriteria(CatalogFilter.parse("name:al;regionCode:1"));
        PageResult<Catalog> page = adapter.findPage(TestCatalogTypes.PROVINCE, "en", criteria, new Paging(0, 1, "name", "ASC"));

        assertThat(page.content()).extracting(c -> c.translation("en").get().name()).containsExactly("Almería");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un filtro sobre un campo que no es adicional de este tipo nunca llega al JPQL")
    void findAll_rejectsFilterOnFieldNotOfType() {
        var criteria = new CatalogSearchCriteria(List.of(new CatalogFilter("regionCode", "1")));

        assertThatThrownBy(() -> adapter.findAll(TestCatalogTypes.REGION, "en", criteria, "name", "ASC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}] enabled admite varios valores (true,false = todas) y rechaza (422) un valor no booleano")
    @MethodSource("catalogTypes")
    void findAll_enabledFilterValues(CatalogType type) {
        adapter.save(sample(type, "Logitech", null));
        adapter.save(sample(type, "Samsung", null).toBuilder().enabled(false).build());

        var both = new CatalogSearchCriteria(List.of(new CatalogFilter("enabled", "true", "false")));
        assertThat(adapter.findAll(type, "en", both, "name", "ASC")).hasSize(2);

        var invalid = new CatalogSearchCriteria(List.of(new CatalogFilter("enabled", "maybe")));
        assertThatThrownBy(() -> adapter.findAll(type, "en", invalid, "name", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    private Catalog province(String name, int ineCode, int regionCode) {
        return adapter.save(Catalog.builder().type(TestCatalogTypes.PROVINCE)
                .extraFields(Map.of("ineCode", ineCode, "regionCode", regionCode))
                .translations(List.of(new CatalogTranslation("en", name, null)))
                .build());
    }

    @Test
    @DisplayName("extraFieldTypes deduce el tipo de dato de la columna de la entidad: ineCode/regionCode son enteros")
    void extraFieldTypes_resolvesDataTypeFromEntity() {
        assertThat(adapter.extraFieldTypes(TestCatalogTypes.PROVINCE))
                .containsExactly(Map.entry("ineCode", "integer"), Map.entry("regionCode", "integer"));
        assertThat(adapter.extraFieldTypes(TestCatalogTypes.REGION)).containsExactly(Map.entry("ineCode", "integer"));
    }

    @Test
    @DisplayName("Filtros numéricos reales: igualdad, IN y rangos sobre columnas Integer (valores convertidos a Integer, no Long)")
    void findAll_filtersByNumericExtraFields() {
        province("Almería", 4, 1);
        province("Cádiz", 11, 1);
        province("Huesca", 22, 2);
        province("Madrid", 28, 13);

        assertThat(adapter.findAll(TestCatalogTypes.PROVINCE, "en",
                new CatalogSearchCriteria(CatalogFilter.parse("regionCode:1")), "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Almería", "Cádiz");
        assertThat(adapter.findAll(TestCatalogTypes.PROVINCE, "en",
                new CatalogSearchCriteria(CatalogFilter.parse("regionCode:2,13")), "name", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Huesca", "Madrid");
        assertThat(adapter.findAll(TestCatalogTypes.PROVINCE, "en",
                new CatalogSearchCriteria(CatalogFilter.parse("ineCode.gte:11;ineCode.lt:28")), "ineCode", "ASC"))
                .extracting(c -> c.translation("en").get().name()).containsExactly("Cádiz", "Huesca");
    }

    @Test
    @DisplayName("Un valor no numérico en un filtro de campo entero es 422")
    void findAll_rejectsNonNumericValueForIntegerField() {
        var criteria = new CatalogSearchCriteria(CatalogFilter.parse("regionCode:andalucia"));

        assertThatThrownBy(() -> adapter.findAll(TestCatalogTypes.PROVINCE, "en", criteria, "name", "ASC"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("regionCode");
    }
}
