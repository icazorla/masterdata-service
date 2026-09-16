package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Adapter-local mapper between the JPA {@link BrandEntity} and the domain
 * {@link Brand}. This is intentionally NOT exposed through any port —
 * it is an implementation detail of the persistence adapter.
 */
@Mapper(componentModel = "spring")
public interface BrandEntityMapper {

    Brand toDomain(BrandEntity entity);

    @Mapping(target = "id", source = "id")
    BrandEntity toEntity(Brand brand);
}
