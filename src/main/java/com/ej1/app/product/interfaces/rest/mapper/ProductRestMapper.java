package com.ej1.app.product.interfaces.rest.mapper;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductPage;
import com.ej1.app.product.domain.port.ProductSearchCriteria;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.interfaces.rest.dto.ProductPageResponse;
import com.ej1.app.product.interfaces.rest.dto.ProductQueryRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductRequest;
import com.ej1.app.product.interfaces.rest.dto.ProductResponse;

/**
 * REST mapper port. Translates between HTTP DTOs ({@link ProductRequest},
 * {@link ProductResponse}) and the domain types ({@link Product},
 * {@link CreateProductCommand}, {@link UpdateProductCommand}).
 *
 * <p>This interface lives in the inbound adapter's mapper package and carries
 * no Spring or MapStruct annotations. The infrastructure-side adapter
 * ({@code ProductRestMapperAdapter}) implements it; Spring DI resolves the
 * concrete bean at runtime.
 */
public interface ProductRestMapper {

    ProductResponse toResponse(Product product);

    CreateProductCommand toCreateCommand(ProductRequest request);

    UpdateProductCommand toUpdateCommand(ProductRequest request);

    /**
     * Projects an existing {@link Product} onto the writable {@link ProductRequest} shape
     * (excludes {@code id}/{@code createdAt}/{@code updatedAt}). Used as the JSON Patch
     * (RFC 6902) base document for {@code PATCH /api/products/{id}} — patch operations can
     * only target fields present in this document, so server-controlled fields are
     * implicitly protected.
     */
    ProductRequest toRequest(Product product);

    /**
     * Translates the HTTP query body ({@link ProductQueryRequest}) into the
     * domain's {@link ProductSearchCriteria} — mirrors {@link #toCreateCommand}/
     * {@link #toUpdateCommand} for the query use case.
     */
    ProductSearchCriteria toCriteria(ProductQueryRequest request);

    /**
     * Projects a {@link ProductPage} onto its HTTP representation. Element mapping
     * reuses {@link #toResponse(Product)}.
     */
    ProductPageResponse toPageResponse(ProductPage page);
}
