package com.ej1.app.common.catalog.infrastructure.persistence;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
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

/**
 * {@link CatalogRepositoryAdapter} es una única clase que sirve a todos los
 * {@link CatalogType} — así que, a diferencia de los antiguos
 * {@code BrandRepositoryAdapterTest}/{@code CategoryRepositoryAdapterTest} por
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
        return Stream.of(TestCatalogTypes.BRAND, TestCatalogTypes.CATEGORY);
    }

    @Autowired
    private CatalogRepositoryAdapter adapter;

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

    private static final CatalogSearchCriteria NO_FILTER = new CatalogSearchCriteria(null, null);

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

        PageResult<Catalog> result = adapter.findPage(type, "en", new CatalogSearchCriteria("logi", null),
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

    @Test
    void save_persistsAndReturnsBrandOnlyExtraField() {
        Catalog brand = Catalog.builder().type(TestCatalogTypes.BRAND)
                .extraFields(Map.of("website", "https://www.logitech.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", null)))
                .build();

        Catalog saved = adapter.save(brand);

        assertThat(saved.getExtraFields()).containsEntry("website", "https://www.logitech.com");
        assertThat(adapter.findById(TestCatalogTypes.BRAND, saved.getId()).get().getExtraFields())
                .containsEntry("website", "https://www.logitech.com");
    }

    @Test
    void save_updatesBrandOnlyExtraField() {
        Catalog saved = adapter.save(Catalog.builder().type(TestCatalogTypes.BRAND)
                .extraFields(Map.of("website", "https://old.example.com"))
                .translations(List.of(new CatalogTranslation("en", "Logitech", null)))
                .build());

        Catalog updated = adapter.save(saved.toBuilder()
                .extraFields(Map.of("website", "https://new.example.com"))
                .build());

        assertThat(updated.getExtraFields()).containsEntry("website", "https://new.example.com");
    }

    @Test
    void findById_categoryHasNoExtraFields() {
        Catalog saved = adapter.save(sample(TestCatalogTypes.CATEGORY, "Peripherals", null));

        Catalog result = adapter.findById(TestCatalogTypes.CATEGORY, saved.getId()).get();

        assertThat(result.getExtraFields()).isEmpty();
    }

    @Test
    void sortableExtraFields_returnsWebsiteOnlyForBrand() {
        assertThat(adapter.sortableExtraFields(TestCatalogTypes.BRAND)).containsExactly("website");
        assertThat(adapter.sortableExtraFields(TestCatalogTypes.CATEGORY)).isEmpty();
    }

    @Test
    @DisplayName("extraFieldTypes deduce el tipo de dato de la columna de la entidad: website es string en brand")
    void extraFieldTypes_resolvesDataTypeFromEntity() {
        assertThat(adapter.extraFieldTypes(TestCatalogTypes.BRAND)).containsExactly(Map.entry("website", "string"));
        assertThat(adapter.extraFieldTypes(TestCatalogTypes.CATEGORY)).isEmpty();
    }

    @Test
    @DisplayName("findAll ordena brand por website (campo adicional del tipo, no traducido), sin coalesce ni join extra")
    void findAll_sortsBrandByExtraField() {
        adapter.save(Catalog.builder().type(TestCatalogTypes.BRAND).extraFields(Map.of("website", "https://zeta.example.com"))
                .translations(List.of(new CatalogTranslation("en", "Samsung", null))).build());
        adapter.save(Catalog.builder().type(TestCatalogTypes.BRAND).extraFields(Map.of("website", "https://alpha.example.com"))
                .translations(List.of(new CatalogTranslation("en", "Acme", null))).build());

        List<Catalog> result = adapter.findAll(TestCatalogTypes.BRAND, "en", NO_FILTER, "website", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Acme", "Samsung");
    }

    @Test
    @DisplayName("findAll rechaza sortBy=website para category: no está en sus extraFields configurados")
    void findAll_rejectsExtraFieldNotConfiguredForType() {
        adapter.save(sample(TestCatalogTypes.CATEGORY, "Peripherals", null));

        assertThatThrownBy(() -> adapter.findAll(TestCatalogTypes.CATEGORY, "en", NO_FILTER, "website", "ASC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}] findAll filtra por nombre (subcadena, sin distinguir mayúsculas)")
    @MethodSource("catalogTypes")
    void findAll_filtersByName(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "en", new CatalogSearchCriteria("logi", null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll filtra por descripción (subcadena, sin distinguir mayúsculas)")
    @MethodSource("catalogTypes")
    void findAll_filtersByDescription(CatalogType type) {
        adapter.save(sample(type, "Samsung", "Electronics maker"));
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "en", new CatalogSearchCriteria(null, "peripheral"), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll combina name y description con OR, no con AND")
    @MethodSource("catalogTypes")
    void findAll_combinesNameAndDescriptionWithOr(CatalogType type) {
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer")); // solo coincide por name
        adapter.save(sample(type, "Acme", "Bluetooth mouse maker")); // solo coincide por description
        adapter.save(sample(type, "Samsung", "Electronics manufacturer")); // no coincide por ninguno

        List<Catalog> result = adapter.findAll(type, "en", new CatalogSearchCriteria("logi", "mouse"), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactlyInAnyOrder("Logitech", "Acme");
    }

    @ParameterizedTest(name = "[{0}] findAll devuelve vacío cuando ningún nombre coincide con el filtro")
    @MethodSource("catalogTypes")
    void findAll_returnsEmptyWhenNameFilterMatchesNothing(CatalogType type) {
        adapter.save(sample(type, "Samsung", null));

        List<Catalog> result = adapter.findAll(type, "en", new CatalogSearchCriteria("no-existe", null), "name", "ASC");

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

        List<Catalog> result = adapter.findAll(type, "es", new CatalogSearchCriteria(null, "periféricos"), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] findAll recurre al idioma por defecto cuando la entrada no tiene traducción para el locale pedido")
    @MethodSource("catalogTypes")
    void findAll_fallsBackToDefaultLocaleWhenEntryHasNoTranslationForRequestedLocale(CatalogType type) {
        // Solo tiene traducción en inglés (el idioma por defecto en este test) — filtrar/ordenar
        // en "es" debe seguir encontrándola gracias al fallback, igual que Catalog#resolveTranslation.
        adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        List<Catalog> result = adapter.findAll(type, "es", new CatalogSearchCriteria("logi", null), "name", "ASC");

        assertThat(result).extracting(c -> c.translation("en").get().name())
                .containsExactly("Logitech");
    }

    @ParameterizedTest(name = "[{0}] deleteById elimina la entrada")
    @MethodSource("catalogTypes")
    void deleteById_removes(CatalogType type) {
        Catalog saved = adapter.save(sample(type, "Logitech", "Peripheral manufacturer"));

        adapter.deleteById(type, saved.getId());

        assertThat(adapter.findById(type, saved.getId())).isEmpty();
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
}
