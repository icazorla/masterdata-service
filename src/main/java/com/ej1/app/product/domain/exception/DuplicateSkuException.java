package com.ej1.app.product.domain.exception;

import com.ej1.app.shared.domain.exception.DuplicateResourceException;

public class DuplicateSkuException extends DuplicateResourceException {
    public DuplicateSkuException(String sku) {
        super("Product with sku '" + sku + "' already exists");
    }
}
