package com.ej1.app.product.domain.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found: id=" + id);
    }

    public ProductNotFoundException(String identifier, boolean bySku) {
        super("Product not found: sku=" + identifier);
    }
}