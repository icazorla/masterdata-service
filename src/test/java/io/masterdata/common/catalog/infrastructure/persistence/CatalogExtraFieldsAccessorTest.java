package io.masterdata.common.catalog.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    @DisplayName("isText solo es cierto para propiedades String")
    void isTextOnlyForStrings() {
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "label")).isTrue();
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "stock")).isFalse();
    }
}
