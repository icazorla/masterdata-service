package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.port.ProductSearchCriteria;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Construye una {@link Specification} JPA a partir de un {@link ProductSearchCriteria}.
 * Vive en {@code infrastructure} — {@code Specification} es un tipo de Spring Data
 * JPA, así que el puerto de dominio solo ve siempre el simple record de criterios.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<ProductEntity> from(ProductSearchCriteria criteria) {
        // Combina solo los filtros realmente presentes, plegando con AND. Encadenar en su
        // lugar Specification.where(null).and(...) dependería de la conveniencia "Specification
        // null == sin restricción" que Spring Data NO aplica de forma consistente entre
        // versiones — SimpleJpaRepository.findAll rechaza directamente una Specification
        // null ("Specification must not be null"), así que unos criterios todos null tienen
        // que recurrir a un predicado explícito siempre-verdadero en vez de una referencia null.
        return Stream.of(
                        fetchCategoryAndBrand(),
                        containsIgnoreCase("name", criteria.name()),
                        containsIgnoreCase("sku", criteria.sku()),
                        inIds("category", criteria.categoryId()),
                        inIds("brand", criteria.brandId()),
                        containsIgnoreCase("tags", criteria.tags()),
                        minPrice(criteria.minPrice()),
                        maxPrice(criteria.maxPrice()),
                        active(criteria.active()))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    /**
     * Une (join) de forma anticipada {@code category}/{@code brand} en la misma consulta en
     * vez de dejar que Hibernate cargue cada una perezosamente fila a fila para cada producto
     * de la página — {@code Product.categoryName}/{@code brandName} las necesitan pobladas
     * en cada lectura. Se omite para la consulta {@code COUNT} que Spring Data ejecuta junto
     * a la consulta de contenido para la paginación: ahí no se permite un fetch
     * ({@code query.getResultType() == Long.class} la identifica), y un conteo de filas
     * de todos modos no necesita los datos unidos.
     */
    private static Specification<ProductEntity> fetchCategoryAndBrand() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category", JoinType.LEFT);
                root.fetch("brand", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    private static Specification<ProductEntity> containsIgnoreCase(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    /**
     * Coincide cuando la asociación llamada {@code field} (p. ej. {@code category}, un
     * {@code @ManyToOne}) tiene un id incluido en {@code ids} — el equivalente a un OR
     * entre varios valores exactos. {@code null}/vacío no aplica ninguna restricción.
     */
    private static Specification<ProductEntity> inIds(String field, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        return (root, query, cb) -> root.get(field).get("id").in(ids);
    }

    private static Specification<ProductEntity> minPrice(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    private static Specification<ProductEntity> maxPrice(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private static Specification<ProductEntity> active(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
