package com.ej1.app.product.domain.exception;

import com.ej1.app.common.domain.exception.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(Long id) {
        super("Product not found: id=" + id, "error.product.notFoundById", id);
    }

    public ProductNotFoundException(String identifier, boolean bySku) {
        super("Product not found: sku=" + identifier, "error.product.notFoundBySku", identifier);
    }
}
