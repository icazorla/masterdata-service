package com.ej1.app.category.infrastructure.rest;

import com.ej1.app.category.interfaces.rest.mapper.CategoryRestMapper;
import org.mapstruct.Mapper;

/**
 * MapStruct-backed implementation of {@link CategoryRestMapper}. Every property
 * on {@link com.ej1.app.category.domain.port.CreateCategoryCommand}/
 * {@link com.ej1.app.category.domain.port.UpdateCategoryCommand} has a
 * same-named counterpart on {@code CategoryRequest}, so plain field-by-field
 * mapping is enough — no hand-written overrides needed.
 */
@Mapper(componentModel = "spring")
public interface CategoryRestMapperAdapter extends CategoryRestMapper {
}
