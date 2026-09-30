package com.ej1.app.product.domain.port;

import java.math.BigDecimal;

/**
 * Objeto de comando que representa la intención de actualizar un producto existente.
 * Se mantiene separado de {@link CreateProductCommand} para que la firma del caso de
 * uso sea explícita y el contrato de las actualizaciones pueda evolucionar de forma independiente.
 */
public record UpdateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String sku,
        Long categoryId,
        Long brandId,
        String imageUrl,
        BigDecimal weight,
        String dimensions,
        String tags,
        Boolean active
) {
}
