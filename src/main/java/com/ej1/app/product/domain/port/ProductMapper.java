package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;

/**
 * Domain mapper port. Maps {@link CreateProductCommand} / {@link UpdateProductCommand}
 * to the {@link Product} aggregate.
 *
 * <p>This interface is part of the domain contract but carries no Spring or MapStruct
 * annotations — those concerns are pushed to the infrastructure adapter
 * ({@code ProductMapperAdapter}), which implements this port and is bound to Spring.
 */
public interface ProductMapper {

    Product toDomain(CreateProductCommand cmd);

    Product toDomain(UpdateProductCommand cmd);

    default Product applyUpdate(Product existing, UpdateProductCommand cmd) {
        return existing.toBuilder()
                .name(cmd.name())
                .description(cmd.description())
                .price(cmd.price())
                .stock(cmd.stock())
                .sku(cmd.sku())
                .category(cmd.category())
                .brand(cmd.brand())
                .imageUrl(cmd.imageUrl())
                .weight(cmd.weight())
                .dimensions(cmd.dimensions())
                .tags(cmd.tags())
                .active(cmd.active() == null ? existing.getActive() : cmd.active())
                .build();
    }
}
