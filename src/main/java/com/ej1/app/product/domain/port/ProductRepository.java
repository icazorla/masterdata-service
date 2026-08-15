package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;

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
     * Runs a filtered, sorted, paginated search. See {@link ProductSearchCriteria}
     * for the query shape.
     */
    ProductPage search(ProductSearchCriteria criteria);
}
