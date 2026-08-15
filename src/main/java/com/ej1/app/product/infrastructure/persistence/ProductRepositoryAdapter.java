package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductPage;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
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
        return mapper.toDomain(jpa.save(mapper.toEntity(product)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public ProductPage search(ProductSearchCriteria criteria) {
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.sortDirection()), criteria.sortBy());
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), sort);
        var result = jpa.findAll(ProductSpecifications.from(criteria), pageable);
        return new ProductPage(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
