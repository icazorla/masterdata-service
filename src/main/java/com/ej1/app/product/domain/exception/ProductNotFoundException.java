package com.ej1.app.product.domain.exception;

import com.ej1.app.shared.domain.exception.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(Long id) {
        super("Product not found: id=" + id);
    }

    public ProductNotFoundException(String identifier, boolean bySku) {
        super("Product not found: sku=" + identifier);
    }
}
