package com.ej1.app.category.domain.exception;

import com.ej1.app.shared.domain.exception.DuplicateResourceException;

public class DuplicateCategoryNameException extends DuplicateResourceException {
    public DuplicateCategoryNameException(String name) {
        super("Category with name '" + name + "' already exists", "error.category.duplicateName", name);
    }
}
