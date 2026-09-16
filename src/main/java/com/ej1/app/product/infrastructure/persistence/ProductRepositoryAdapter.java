package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.shared.domain.PageResult;
import com.ej1.app.shared.domain.Paging;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpa;
    private final ProductEntityMapper mapper;
    private final EntityManager entityManager;

    @Override
    public Optional<Product> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return jpa.findBySku(sku).map(mapper::toDomain);
    }

    @Override
    public boolean existsBySku(String sku) {
        return jpa.existsBySku(sku);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public Product save(Product product) {
        // mapper.toEntity resolves category/brand via EntityManager.getReference — a real
        // Hibernate proxy, not a detached POJO — so toDomain's category.name/brand.name
        // mapping lazily initializes it here (one SELECT) instead of coming back null.
        // A plain re-fetch wouldn't achieve the same thing: within this same persistence
        // context, jpa.findById(id) right after save() would just return the identity-mapped
        // instance we already hold, unqueried, ignoring any @EntityGraph.
        return mapper.toDomain(jpa.save(mapper.toEntity(product, entityManager)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public PageResult<Product> search(ProductSearchCriteria criteria, Paging paging) {
        Sort sort = Sort.by(Sort.Direction.fromString(paging.sortDirection()), toEntitySortProperty(paging.sortBy()));
        Pageable pageable = PageRequest.of(paging.page(), paging.size(), sort);
        var result = jpa.findAll(ProductSpecifications.from(criteria), pageable);
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * {@code categoryId}/{@code brandId} are {@code Product}'s (domain) sortable field
     * names — {@link com.ej1.app.product.domain.service.ProductService#ALLOWED_SORT_FIELDS}
     * validates against those. {@link ProductEntity} has no such property, only the
     * {@code category}/{@code brand} associations, so a domain-facing sort name is
     * translated to the JPA property path (nested, resolved via a join) here, at the
     * infrastructure boundary — the only layer that knows about {@link ProductEntity}'s shape.
     */
    private static String toEntitySortProperty(String sortBy) {
        return switch (sortBy) {
            case "categoryId" -> "category.id";
            case "brandId" -> "brand.id";
            default -> sortBy;
        };
    }
}
