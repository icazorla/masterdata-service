package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.port.BrandMapper;
import org.mapstruct.Mapper;

/**
 * Infrastructure adapter that implements the {@link BrandMapper} domain port.
 * MapStruct generates {@code BrandMapperAdapterImpl}, registered as a Spring
 * bean via {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface BrandMapperAdapter extends BrandMapper {
}
