package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conversión de los valores de filtro al tipo Java real de cada columna. Las entidades de
 * catálogo actuales solo tienen campos adicionales de texto, así que se prueba sobre un
 * JavaBean con un campo de cada tipo soportado.
 */
class CatalogExtraFieldsAccessorTest {

    @Getter
    @Setter
    static class SampleBean {
        private String label;
        private Integer stock;
        private BigDecimal price;
        private Boolean featured;
        private LocalDate launchDate;
        private LocalDateTime publishedAt;
    }

    @Test
    @DisplayName("convert devuelve el tipo Java exacto de la propiedad (Integer, no Long), con fechas en ISO-8601")
    void convertsToExactPropertyType() {
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "label", "acme")).isEqualTo("acme");
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "stock", "42")).isEqualTo(42);
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "price", "9.95")).isEqualTo(new BigDecimal("9.95"));
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "featured", "true")).isEqualTo(true);
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "launchDate", "2026-10-13"))
                .isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(CatalogExtraFieldsAccessor.convert(SampleBean.class, "publishedAt", "2026-10-13T08:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 10, 13, 8, 30));
    }

    @Test
    @DisplayName("convert lanza IllegalArgumentException cuando el valor no encaja con el tipo")
    void rejectsValuesThatDoNotMatchType() {
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.convert(SampleBean.class, "stock", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.convert(SampleBean.class, "launchDate", "13/10/2026"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.convert(SampleBean.class, "featured", "maybe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("write convierte cada valor del payload al tipo de la propiedad")
    void writeConvertsPayloadValues() {
        SampleBean bean = new SampleBean();
        Map<String, Object> values = new HashMap<>();
        values.put("stock", "42");
        values.put("price", 9.95);
        values.put("featured", true);
        values.put("launchDate", "2026-10-13");
        values.put("label", null);

        CatalogExtraFieldsAccessor.write(bean, List.of("stock", "price", "featured", "launchDate", "label"), values);

        assertThat(bean.getStock()).isEqualTo(42);
        assertThat(bean.getPrice()).isEqualByComparingTo("9.95");
        assertThat(bean.getFeatured()).isTrue();
        assertThat(bean.getLaunchDate()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(bean.getLabel()).isNull();
    }

    @Test
    @DisplayName("write rechaza con 422 un valor que no encaja con el tipo, en vez de truncarlo o dar 500")
    void writeRejectsValuesThatDoNotMatchType() {
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.write(new SampleBean(), List.of("stock"), Map.of("stock", "abc")))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("'stock'");
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.write(new SampleBean(), List.of("stock"), Map.of("stock", 5.5)))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    @DisplayName("write rechaza un atributo que no es un campo del tipo, sin escribir nada")
    void writeRejectsUnknownFields() {
        SampleBean bean = new SampleBean();
        bean.setStock(7);

        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.write(bean, List.of("stock"), Map.of("stok", 42)))
                .isInstanceOf(InvalidCatalogQueryException.class)
                .hasMessageContaining("'stok'");
        assertThat(bean.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("isText solo es cierto para propiedades String")
    void isTextOnlyForStrings() {
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "label")).isTrue();
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "stock")).isFalse();
    }
}
