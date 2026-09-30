package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;

import java.util.Optional;

/**
 * Puerto de salida para la persistencia de productos. El dominio define lo que
 * necesita del mundo exterior; los adaptadores de infraestructura implementan este contrato.
 */
public interface ProductRepository {

    Optional<Product> findById(Long id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsById(Long id);

    Product save(Product product);

    void deleteById(Long id);

    /**
     * Ejecuta una búsqueda filtrada, ordenada y paginada. {@code criteria} transporta solo
     * filtros (ver {@link ProductSearchCriteria}); {@code paging} transporta page/size/sort — se
     * mantienen separados para que cada uno sea la única fuente de verdad de su propia responsabilidad.
     */
    PageResult<Product> search(ProductSearchCriteria criteria, Paging paging);
}
