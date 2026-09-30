package com.ej1.app.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    /**
     * {@code category}/{@code brand} son {@code fetch = LAZY} en {@link ProductEntity} (el valor
     * por defecto seguro para un {@code @ManyToOne}); este entity graph las carga en la misma
     * consulta en vez de perezosamente fila a fila, ya que el dominio
     * ({@code Product.categoryName}/{@code brandName}) las necesita pobladas siempre que se lee
     * un único producto.
     */
    @EntityGraph(attributePaths = {"category", "brand"})
    @Override
    Optional<ProductEntity> findById(Long id);

    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<ProductEntity> findBySku(String sku);

    boolean existsBySku(String sku);
}
