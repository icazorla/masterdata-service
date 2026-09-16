package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;
import com.ej1.app.shared.domain.PageResult;
import com.ej1.app.shared.domain.Paging;

import java.util.Optional;

/**
 * Outgoing port for product persistence. The domain defines what it needs
 * from the outside world; infrastructure adapters implement this contract.
 */
public interface ProductRepository {

    Optional<Product> findById(Long id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsById(Long id);

    Product save(Product product);

    void deleteById(Long id);

    /**
     * Runs a filtered, sorted, paginated search. {@code criteria} carries only filters
     * (see {@link ProductSearchCriteria}); {@code paging} carries page/size/sort — kept
     * separate so each is a single source of truth for its own concern.
     */
    PageResult<Product> search(ProductSearchCriteria criteria, Paging paging);
}
