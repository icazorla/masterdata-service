package com.ej1.app.product.domain.service;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.service.CatalogService;
import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.exception.DuplicateSkuException;
import com.ej1.app.product.domain.exception.InvalidProductQueryException;
import com.ej1.app.product.domain.exception.ProductNotFoundException;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductMapper;
import com.ej1.app.product.domain.port.ProductRepository;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.common.domain.PageResult;
import com.ej1.app.common.domain.Paging;
import com.ej1.app.common.domain.SortableFieldValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Servicio de dominio (caso de uso) para el agregado de producto.
 * Vive en la capa de dominio; depende únicamente de sus propios puertos.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * Nombres de campo que {@code search} acepta para {@code sortBy}. Vive aquí, no en
     * {@link ProductSearchCriteria}, porque es conocimiento específico sobre el agregado
     * {@code Product} — el servicio de una futura entidad declararía su propio conjunto.
     * Ordenar por un campo no listado se rechaza como regla de negocio (422), igual que
     * {@code minPrice > maxPrice} más abajo, en lugar de como una restricción de formato
     * de un solo campo — se aplica mediante el {@link SortableFieldValidator} compartido,
     * que solo es responsable de la mecánica de comprobar-y-lanzar, no de este conjunto
     * ni del tipo de excepción.
     *
     * <p>Package-private (no {@code private}) únicamente para que un test del mismo paquete
     * pueda verificar, mediante reflexión sobre {@link Product}, que cada nombre aquí sigue
     * nombrando un getter real — nada aquí liga este {@code Set<String>} a los campos reales
     * de {@code Product} en tiempo de compilación, así que un renombrado se desincronizaría
     * silenciosamente hasta romperse en tiempo de ejecución. Ver
     * {@code ProductServiceSortFieldsTest}.
     */
    static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "price", "stock", "sku", "categoryId", "brandId", "createdAt", "updatedAt");

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final CatalogService catalogService;

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
        requireCategoryAndBrandExist(cmd.categoryId(), cmd.brandId());
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
        requireCategoryAndBrandExist(cmd.categoryId(), cmd.brandId());

        Product updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    /**
     * {@code categoryId}/{@code brandId} referencian un agregado distinto (datos maestros),
     * así que su existencia se comprueba aquí en lugar de dejar que una violación de FK
     * aflore más tarde como un error de base de datos opaco.
     */
    private void requireCategoryAndBrandExist(Long categoryId, Long brandId) {
        if (categoryId != null && !catalogService.existsById(CatalogType.CATEGORY, categoryId)) {
            throw new CatalogNotFoundException(CatalogType.CATEGORY, categoryId);
        }
        if (brandId != null && !catalogService.existsById(CatalogType.BRAND, brandId)) {
            throw new CatalogNotFoundException(CatalogType.BRAND, brandId);
        }
    }

    /**
     * Búsqueda de productos filtrada, ordenada y paginada. {@code criteria} transporta solo
     * filtros; {@code paging} transporta page/size/sort, pasados por separado para que cada
     * uno sea la única fuente de verdad de su propia responsabilidad (ver
     * {@link ProductSearchCriteria}). Valida reglas de negocio que una restricción de Bean
     * Validation de un solo campo no puede expresar (ver {@link InvalidProductQueryException})
     * — los límites de {@code page}/{@code size} y que {@code sortDirection} sea
     * {@code ASC}/{@code DESC} ya están garantizados como bien formados por Bean Validation
     * en la capa REST; que {@code sortBy} nombre un campo realmente ordenable es conocimiento
     * de negocio específico de Product, así que se comprueba aquí en su lugar.
     */
    @Transactional(readOnly = true)
    public PageResult<Product> search(ProductSearchCriteria criteria, Paging paging) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new InvalidProductQueryException(
                    "minPrice (%s) must not be greater than maxPrice (%s)"
                            .formatted(criteria.minPrice(), criteria.maxPrice()),
                    "error.product.invalidPriceRange", criteria.minPrice(), criteria.maxPrice());
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
