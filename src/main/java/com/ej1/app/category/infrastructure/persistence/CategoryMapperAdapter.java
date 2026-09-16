package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.port.CategoryMapper;
import org.mapstruct.Mapper;

/**
 * Infrastructure adapter that implements the {@link CategoryMapper} domain port.
 * MapStruct generates {@code CategoryMapperAdapterImpl}, registered as a Spring
 * bean via {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapperAdapter extends CategoryMapper {
}
