package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.Brand;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Adapter-local mapper between the JPA {@link BrandEntity} and the domain
 * {@link Brand}. This is intentionally NOT exposed through any port —
 * it is an implementation detail of the persistence adapter.
 *
 * <p>{@code toEntity}'s generated mapping copies {@code Brand.translations} into
 * {@code BrandEntity.translations} element-by-element with no knowledge of the
 * owning side; {@link #linkTranslations} wires each child's {@code brand}
 * back-reference afterward, since {@code brand_id} is read off that reference,
 * not off the collection.
 */
@Mapper(componentModel = "spring")
public interface BrandEntityMapper {

    Brand toDomain(BrandEntity entity);

    @Mapping(target = "id", source = "id")
    BrandEntity toEntity(Brand brand);

    @AfterMapping
    default void linkTranslations(@MappingTarget BrandEntity entity) {
        entity.getTranslations().forEach(t -> t.setBrand(entity));
    }
}
