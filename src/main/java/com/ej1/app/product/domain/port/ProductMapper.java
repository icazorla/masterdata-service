package com.ej1.app.product.domain.port;

import com.ej1.app.product.domain.Product;

/**
 * Puerto del mapper de dominio. Mapea {@link CreateProductCommand} / {@link UpdateProductCommand}
 * hacia el agregado {@link Product}.
 *
 * <p>Esta interfaz forma parte del contrato de dominio pero no lleva anotaciones de
 * Spring ni de MapStruct — esas responsabilidades se delegan al adaptador de infraestructura
 * ({@code ProductMapperAdapter}), que implementa este puerto y está ligado a Spring.
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
                .categoryId(cmd.categoryId())
                .brandId(cmd.brandId())
                .imageUrl(cmd.imageUrl())
                .weight(cmd.weight())
                .dimensions(cmd.dimensions())
                .tags(cmd.tags())
                .active(cmd.active() == null ? existing.getActive() : cmd.active())
                .build();
    }
}
