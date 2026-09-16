package com.ej1.app.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    /**
     * {@code category}/{@code brand} are {@code fetch = LAZY} on {@link ProductEntity} (the safe
     * default for a {@code @ManyToOne}); this entity graph loads them in the same query instead of
     * lazily one row at a time, since the domain ({@code Product.categoryName}/{@code brandName})
     * needs them populated whenever a single product is read.
     */
    @EntityGraph(attributePaths = {"category", "brand"})
    @Override
    Optional<ProductEntity> findById(Long id);

    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<ProductEntity> findBySku(String sku);

    boolean existsBySku(String sku);
}
