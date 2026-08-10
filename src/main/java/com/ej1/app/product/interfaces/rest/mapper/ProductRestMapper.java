package com.ej1.app.product.interfaces.rest.mapper;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.UpdateProductCommand;
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
}
