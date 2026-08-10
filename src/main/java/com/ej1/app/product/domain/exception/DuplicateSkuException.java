package com.ej1.app.product.domain.exception;

public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String sku) {
        super("Product with sku '" + sku + "' already exists");
    }
}