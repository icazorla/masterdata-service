package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Adapter-local mapper between the JPA {@link CategoryEntity} and the domain
 * {@link Category}. This is intentionally NOT exposed through any port —
 * it is an implementation detail of the persistence adapter.
 *
 * <p>{@code toEntity}'s generated mapping copies {@code Category.translations} into
 * {@code CategoryEntity.translations} element-by-element with no knowledge of the
 * owning side; {@link #linkTranslations} wires each child's {@code category}
 * back-reference afterward, since {@code category_id} is read off that reference,
 * not off the collection.
 */
@Mapper(componentModel = "spring")
public interface CategoryEntityMapper {

    Category toDomain(CategoryEntity entity);

    @Mapping(target = "id", source = "id")
    CategoryEntity toEntity(Category category);

    @AfterMapping
    default void linkTranslations(@MappingTarget CategoryEntity entity) {
        entity.getTranslations().forEach(t -> t.setCategory(entity));
    }
}
