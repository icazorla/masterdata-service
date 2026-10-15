package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        private Double ratio;
        private UUID externalId;
    }

    /** Casos que una entidad real no debería tener, para comprobar que fallan con un mensaje claro. */
    @Getter
    @Setter
    static class BrokenBean {
        /** {@link Temporal} no tiene {@code parse(CharSequence)}. */
        private Temporal moment;
        private Integer failing;

        public String getReadOnly() {
            return "x";
        }

        public Integer getFailing() {
            throw new IllegalStateException("getter failed");
        }

        public void setFailing(Integer failing) {
            throw new IllegalStateException("setter failed");
        }
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
    @DisplayName("convert falla con IllegalStateException si un tipo de fecha no tiene parse(CharSequence)")
    void convertFailsForTemporalWithoutParse() {
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.convert(BrokenBean.class, "moment", "2026-10-13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parse(CharSequence)");
    }

    @Test
    @DisplayName("dataTypes deduce el tipo JSON de cada campo, y string para enums, UUID y similares")
    void dataTypesMapsJavaTypes() {
        assertThat(CatalogExtraFieldsAccessor.dataTypes(SampleBean.class,
                List.of("label", "stock", "price", "ratio", "featured", "launchDate", "publishedAt", "externalId")))
                .containsExactly(
                        Map.entry("label", "string"),
                        Map.entry("stock", "integer"),
                        Map.entry("price", "number"),
                        Map.entry("ratio", "number"),
                        Map.entry("featured", "boolean"),
                        Map.entry("launchDate", "date"),
                        Map.entry("publishedAt", "date-time"),
                        Map.entry("externalId", "string"));
        assertThat(CatalogExtraFieldsAccessor.dataType(SampleBean.class, "stock")).isEqualTo("integer");
    }

    @Test
    @DisplayName("read devuelve el valor de cada campo, en el orden pedido")
    void readReturnsValues() {
        SampleBean bean = new SampleBean();
        bean.setLabel("acme");
        bean.setStock(3);

        assertThat(CatalogExtraFieldsAccessor.read(bean, List.of("stock", "label")))
                .containsExactly(Map.entry("stock", 3), Map.entry("label", "acme"));
    }

    @Test
    @DisplayName("read y write envuelven en IllegalStateException el error del getter o del setter")
    void readAndWriteWrapAccessorFailures() {
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.read(new BrokenBean(), List.of("failing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read extra field 'failing'");
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.write(new BrokenBean(), List.of("failing"), Map.of("failing", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot write extra field 'failing'");
    }

    @Test
    @DisplayName("un campo sin setter, o que no es propiedad de la clase, falla con un mensaje que lo nombra")
    void rejectsFieldsThatAreNotReadWriteProperties() {
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.isText(BrokenBean.class, "readOnly"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'readOnly' is not a JavaBean property with getter and setter");
        assertThatThrownBy(() -> CatalogExtraFieldsAccessor.isText(SampleBean.class, "missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'missing' is not a JavaBean property with getter and setter");
    }

    @Test
    @DisplayName("isText solo es cierto para propiedades String")
    void isTextOnlyForStrings() {
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "label")).isTrue();
        assertThat(CatalogExtraFieldsAccessor.isText(SampleBean.class, "stock")).isFalse();
    }
}
