package io.masterdata.common.catalog.domain.port;

import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogFilter.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogFilterTest {

    @Test
    @DisplayName("Condiciones separadas por ; en el orden escrito; sin sufijo es EQ")
    void parsesConditionsInOrder() {
        assertThat(CatalogFilter.parse("name:logi;description:mouse;enabled:false;website:acme"))
                .containsExactly(
                        new CatalogFilter("name", "logi"),
                        new CatalogFilter("description", "mouse"),
                        new CatalogFilter("enabled", "false"),
                        new CatalogFilter("website", "acme"));
    }

    @Test
    @DisplayName("null, vacío, condiciones vacías y valores vacíos no filtran")
    void emptyInputsAreIgnored() {
        assertThat(CatalogFilter.parse(null)).isEmpty();
        assertThat(CatalogFilter.parse("")).isEmpty();
        assertThat(CatalogFilter.parse(" ; ;name:;website: , ")).isEmpty();
    }

    @Test
    @DisplayName("Varios valores separados por comas; se recortan espacios y se descartan los vacíos")
    void commaSeparatedValues() {
        assertThat(CatalogFilter.parse("colorCode: #FFF, #000,,"))
                .containsExactly(new CatalogFilter("colorCode", "#FFF", "#000"));
    }

    @Test
    @DisplayName("Solo cuenta el primer ':', así que URLs y fechas con hora no necesitan escaparse")
    void onlyFirstColonSeparatesKeyFromValue() {
        assertThat(CatalogFilter.parse("website:https://acme.com;publishedAt.gte:2026-10-13T08:30:00"))
                .containsExactly(
                        new CatalogFilter("website", "https://acme.com"),
                        new CatalogFilter("publishedAt", Operator.GTE, List.of("2026-10-13T08:30:00")));
    }

    @Test
    @DisplayName("\\; \\, y \\\\ son literales")
    void escapes() {
        assertThat(CatalogFilter.parse("name:Foo\\, Inc.,A\\;B;description:back\\\\slash"))
                .containsExactly(
                        new CatalogFilter("name", "Foo, Inc.", "A;B"),
                        new CatalogFilter("description", "back\\slash"));
    }

    @Test
    @DisplayName("Los sufijos gt/gte/lt/lte son operadores de rango; el mismo campo puede repetirse")
    void rangeSuffixes() {
        assertThat(CatalogFilter.parse("price.gte:10;price.lt:50;stock.gt:0;stock.lte:9"))
                .containsExactly(
                        new CatalogFilter("price", Operator.GTE, List.of("10")),
                        new CatalogFilter("price", Operator.LT, List.of("50")),
                        new CatalogFilter("stock", Operator.GT, List.of("0")),
                        new CatalogFilter("stock", Operator.LTE, List.of("9")));
        assertThat(new CatalogFilter("price", Operator.GTE, List.of("10")).key()).isEqualTo("price.gte");
    }

    @Test
    @DisplayName("Una condición sin ':' o sin campo es 422")
    void malformedConditionIsRejected() {
        assertThatThrownBy(() -> CatalogFilter.parse("name:logi;oops"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("oops");
        assertThatThrownBy(() -> CatalogFilter.parse(":logi"))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    @DisplayName("Un sufijo que no es un operador conocido es 422")
    void unknownSuffixIsRejected() {
        assertThatThrownBy(() -> CatalogFilter.parse("price.between:1"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("between");
    }

    @Test
    @DisplayName("Un operador de rango con varios valores es 422")
    void rangeWithSeveralValuesIsRejected() {
        assertThatThrownBy(() -> CatalogFilter.parse("price.gte:1,2"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("price.gte");
    }

    @Test
    @DisplayName("Más de MAX_CONDITIONS condiciones o de MAX_VALUES valores en una condición es 422; el límite exacto vale")
    void limitsAreEnforced() {
        String values = String.join(",", java.util.Collections.nCopies(CatalogFilter.MAX_VALUES, "a"));
        String conditions = String.join(";", java.util.Collections.nCopies(CatalogFilter.MAX_CONDITIONS, "name:a"));

        assertThat(CatalogFilter.parse("name:" + values)).singleElement()
                .satisfies(f -> assertThat(f.values()).hasSize(CatalogFilter.MAX_VALUES));
        assertThat(CatalogFilter.parse(conditions)).hasSize(CatalogFilter.MAX_CONDITIONS);
        assertThatThrownBy(() -> CatalogFilter.parse("name:" + values + ",a"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> CatalogFilter.parse(conditions + ";name:a"))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining(String.valueOf(CatalogFilter.MAX_CONDITIONS));
    }

    @Test
    @DisplayName("Operadores por tipo de dato: contains en texto, eq en booleanos, eq y rangos en números y fechas")
    void supportedOperatorsByDataType() {
        assertThat(Operator.supportedNames("string")).containsExactly("contains");
        assertThat(Operator.supportedNames("boolean")).containsExactly("eq");
        assertThat(Operator.supportedNames("integer")).containsExactly("eq", "gt", "gte", "lt", "lte");
        assertThat(Operator.supportedNames("date-time")).containsExactly("eq", "gt", "gte", "lt", "lte");
        assertThat(Operator.GTE.supports("string")).isFalse();
        assertThat(Operator.GTE.supports("number")).isTrue();
        assertThat(Operator.EQ.supports("boolean")).isTrue();
    }

    @Test
    @DisplayName("CatalogSearchCriteria: and añade al final y filtersOnlyByEnabled decide si se cachea")
    void criteriaHelpers() {
        var criteria = new CatalogSearchCriteria(CatalogFilter.parse("name:logi"));
        assertThat(criteria.filtersBy("name")).isTrue();
        assertThat(criteria.filtersOnlyByEnabled()).isFalse();
        assertThat(criteria.and(new CatalogFilter("enabled", "true")).filters())
                .extracting(CatalogFilter::field).containsExactly("name", "enabled");
        assertThat(CatalogSearchCriteria.none().and(new CatalogFilter("enabled", "true")).filtersOnlyByEnabled()).isTrue();
    }
}
