package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.shared.domain.PageResult;
import com.ej1.app.shared.domain.Paging;
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
    public PageResult<Product> search(ProductSearchCriteria criteria, Paging paging) {
        Sort sort = Sort.by(Sort.Direction.fromString(paging.sortDirection()), paging.sortBy());
        Pageable pageable = PageRequest.of(paging.page(), paging.size(), sort);
        var result = jpa.findAll(ProductSpecifications.from(criteria), pageable);
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
