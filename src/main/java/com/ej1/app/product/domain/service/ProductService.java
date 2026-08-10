package com.ej1.app.product.domain.service;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductMapper;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain service (use case) for the product aggregate.
 * Lives in the domain layer; depends only on its own ports.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Product findBySku(String sku) {
        return repository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku, true));
    }

    @Transactional
    public Product create(CreateProductCommand cmd) {
        if (repository.existsBySku(cmd.sku())) {
            throw new DuplicateSkuException(cmd.sku());
        }
        Product draft = mapper.toDomain(cmd);
        return repository.save(draft);
    }

    @Transactional
    public Product update(Long id, UpdateProductCommand cmd) {
        Product existing = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (!existing.getSku().equals(cmd.sku()) && repository.existsBySku(cmd.sku())) {
            throw new DuplicateSkuException(cmd.sku());
        }

        Product updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
