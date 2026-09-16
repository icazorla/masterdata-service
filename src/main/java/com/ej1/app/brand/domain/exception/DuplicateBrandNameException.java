package com.ej1.app.brand.domain.exception;

import com.ej1.app.shared.domain.exception.DuplicateResourceException;

public class DuplicateBrandNameException extends DuplicateResourceException {
    public DuplicateBrandNameException(String name) {
        super("Brand with name '" + name + "' already exists");
    }
}
