package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
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
        // mapper.toEntity resuelve category/brand mediante EntityManager.getReference — un
        // proxy real de Hibernate, no un POJO detached — así que el mapeo
        // category.name/brand.name de toDomain lo inicializa perezosamente aquí (un SELECT)
        // en vez de volver como null. Un simple re-fetch no lograría lo mismo: dentro de este
        // mismo contexto de persistencia, jpa.findById(id) justo después de save() devolvería
        // simplemente la instancia ya mapeada por identidad que ya tenemos, sin consultar,
        // ignorando cualquier @EntityGraph.
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
     * {@code categoryId}/{@code brandId} son los nombres de campo ordenables de
     * {@code Product} (dominio) — {@link com.ej1.app.product.domain.service.ProductService#ALLOWED_SORT_FIELDS}
     * valida contra ellos. {@link ProductEntity} no tiene esa propiedad, solo las
     * asociaciones {@code category}/{@code brand}, así que un nombre de orden orientado
     * al dominio se traduce aquí a la ruta de propiedad JPA (anidada, resuelta mediante
     * un join), en la frontera de infraestructura — la única capa que conoce la forma
     * de {@link ProductEntity}.
     */
    private static String toEntitySortProperty(String sortBy) {
        return switch (sortBy) {
            case "categoryId" -> "category.id";
            case "brandId" -> "brand.id";
            default -> sortBy;
        };
    }
}
