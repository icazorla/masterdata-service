package com.ej1.app.category.domain.exception;

import com.ej1.app.shared.domain.exception.NotFoundException;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException(Long id) {
        super("Category not found: id=" + id, "error.category.notFound", id);
    }
}
