package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.port.ProductSearchCriteria;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Builds a JPA {@link Specification} from a {@link ProductSearchCriteria}.
 * Lives in {@code infrastructure} — {@code Specification} is a Spring Data
 * JPA type, so the domain port only ever sees the plain criteria record.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<ProductEntity> from(ProductSearchCriteria criteria) {
        // Combine only the filters actually present, folding with AND. Chaining
        // Specification.where(null).and(...) instead would rely on the "null
        // Specification == no restriction" convenience Spring Data does NOT
        // apply consistently across versions — SimpleJpaRepository.findAll
        // rejects a null Specification outright ("Specification must not be
        // null"), so an all-null criteria has to fall back to an explicit
        // always-true predicate rather than a null reference.
        return Stream.of(
                        containsIgnoreCase("name", criteria.name()),
                        containsIgnoreCase("sku", criteria.sku()),
                        containsIgnoreCaseAnyOf("category", criteria.category()),
                        containsIgnoreCaseAnyOf("brand", criteria.brand()),
                        containsIgnoreCase("tags", criteria.tags()),
                        minPrice(criteria.minPrice()),
                        maxPrice(criteria.maxPrice()),
                        active(criteria.active()))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    private static Specification<ProductEntity> containsIgnoreCase(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    /**
     * OR's a case-insensitive "contains" match across every given value —
     * a product matches when {@code field} contains any one of them. Blank
     * values are ignored; if none remain, no restriction is applied.
     */
    private static Specification<ProductEntity> containsIgnoreCaseAnyOf(String field, List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return values.stream()
                .map(value -> containsIgnoreCase(field, value))
                .filter(Objects::nonNull)
                .reduce(Specification::or)
                .orElse(null);
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
