package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Adapter-local mapper between the JPA {@link CategoryEntity} and the domain
 * {@link Category}. This is intentionally NOT exposed through any port —
 * it is an implementation detail of the persistence adapter.
 */
@Mapper(componentModel = "spring")
public interface CategoryEntityMapper {

    Category toDomain(CategoryEntity entity);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CategoryEntity toEntity(Category category);
}
