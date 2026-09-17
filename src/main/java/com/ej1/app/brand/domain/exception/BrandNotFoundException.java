package com.ej1.app.brand.domain.exception;

import com.ej1.app.shared.domain.exception.NotFoundException;

public class BrandNotFoundException extends NotFoundException {
    public BrandNotFoundException(Long id) {
        super("Brand not found: id=" + id, "error.brand.notFound", id);
    }
}
