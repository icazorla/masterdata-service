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
     * Eagerly joins {@code category}/{@code brand} in the same query instead of leaving
     * Hibernate to lazily load each one row-by-row for every product in the page —
     * {@code Product.categoryName}/{@code brandName} need them populated on every read.
     * Skipped for the {@code COUNT} query Spring Data runs alongside the content query for
     * paging: a fetch is not allowed there ({@code query.getResultType() == Long.class}
     * identifies it), and a row count doesn't need the joined data anyway.
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
     * Matches when the association named {@code field} (e.g. {@code category}, a
     * {@code @ManyToOne}) has an id in {@code ids} — the equivalent of an OR across
     * several exact values. {@code null}/empty applies no restriction.
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
