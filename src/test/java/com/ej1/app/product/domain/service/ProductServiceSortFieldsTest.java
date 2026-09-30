package com.ej1.app.product.domain.service;

import com.ej1.app.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Protege contra que {@link ProductService#ALLOWED_SORT_FIELDS} se desincronice
 * silenciosamente de los campos reales de {@link Product}. {@code ALLOWED_SORT_FIELDS}
 * es un {@code Set<String>} escrito a mano sin ningún vínculo en tiempo de compilación
 * con {@code Product} — nada impide que un campo de {@code Product} se renombre o
 * elimine sin actualizar el conjunto, y una entrada obsoleta no se detectaría hasta que
 * una petición {@code sortBy} para ese campo fallara más adelante en
 * {@code ProductSpecifications}/JPA en lugar de con un 422 claro. Este test convierte
 * eso en un fallo inmediato y legible.
 */
class ProductServiceSortFieldsTest {

    @Test
    @DisplayName("cada campo en ALLOWED_SORT_FIELDS tiene un getter real en Product")
    void allowedSortFields_matchExistingProductGetters() {
        for (String field : ProductService.ALLOWED_SORT_FIELDS) {
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            assertThatCode(() -> Product.class.getMethod(getter))
                    .as("ALLOWED_SORT_FIELDS contiene '%s' pero Product ya no tiene %s()", field, getter)
                    .doesNotThrowAnyException();
        }
    }
}
