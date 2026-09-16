package com.ej1.app.product.domain.service;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.InvalidProductQueryException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductMapper;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.shared.domain.PageResult;
import com.ej1.app.shared.domain.Paging;
import com.ej1.app.shared.domain.SortableFieldValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Domain service (use case) for the product aggregate.
 * Lives in the domain layer; depends only on its own ports.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * Field names {@code search} accepts for {@code sortBy}. Lives here, not on
     * {@link ProductSearchCriteria}, because it's knowledge about the {@code Product}
     * aggregate specifically — a future entity's service would declare its own set.
     * Sorting by an unlisted field is rejected as a business rule (422), the same way
     * {@code minPrice > maxPrice} is below, rather than as a single-field format constraint —
     * enforced via the shared {@link SortableFieldValidator}, which owns only the
     * check-and-throw mechanics, not this set or the exception type.
     *
     * <p>Package-private (not {@code private}) purely so a same-package test can verify, via
     * reflection over {@link Product}, that every name here still names a real getter — nothing
     * here ties this {@code Set<String>} to {@code Product}'s actual fields at compile time, so a
     * rename would otherwise desync silently until it broke at runtime. See
     * {@code ProductServiceSortFieldsTest}.
     */
    static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "price", "stock", "sku", "category", "brand", "createdAt", "updatedAt");

    private final ProductRepository repository;
    private final ProductMapper mapper;

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

    /**
     * Filtered, sorted, paginated product search. {@code criteria} carries only filters;
     * {@code paging} carries page/size/sort, passed separately so each is a single source
     * of truth for its own concern (see {@link ProductSearchCriteria}). Validates business
     * rules that a single-field Bean Validation constraint cannot express (see
     * {@link InvalidProductQueryException}) — {@code page}/{@code size} bounds and
     * {@code sortDirection} being {@code ASC}/{@code DESC} are already guaranteed
     * well-formed by the REST layer's Bean Validation; {@code sortBy} naming an actual
     * sortable field is Product-specific business knowledge, so it's checked here instead.
     */
    @Transactional(readOnly = true)
    public PageResult<Product> search(ProductSearchCriteria criteria, Paging paging) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new InvalidProductQueryException(
                    "minPrice (%s) must not be greater than maxPrice (%s)"
                            .formatted(criteria.minPrice(), criteria.maxPrice()));
        }
        SortableFieldValidator.requireSortable(paging.sortBy(), ALLOWED_SORT_FIELDS,
                InvalidProductQueryException::new);
        return repository.search(criteria, paging);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
