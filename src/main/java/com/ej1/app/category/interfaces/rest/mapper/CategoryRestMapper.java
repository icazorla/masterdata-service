package com.ej1.app.category.interfaces.rest.mapper;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryResponse;

import java.util.List;

/**
 * REST mapper port. Translates between HTTP DTOs ({@link CategoryRequest},
 * {@link CategoryResponse}) and the domain types ({@link Category},
 * {@link CreateCategoryCommand}, {@link UpdateCategoryCommand}).
 */
public interface CategoryRestMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    CreateCategoryCommand toCreateCommand(CategoryRequest request);

    UpdateCategoryCommand toUpdateCommand(CategoryRequest request);
}
