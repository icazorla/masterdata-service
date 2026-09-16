package com.ej1.app.brand.infrastructure.rest;

import com.ej1.app.brand.interfaces.rest.mapper.BrandRestMapper;
import org.mapstruct.Mapper;

/**
 * MapStruct-backed implementation of {@link BrandRestMapper}. Every property
 * on {@link com.ej1.app.brand.domain.port.CreateBrandCommand}/
 * {@link com.ej1.app.brand.domain.port.UpdateBrandCommand} has a same-named
 * counterpart on {@code BrandRequest}, so plain field-by-field mapping is
 * enough — no hand-written overrides needed.
 */
@Mapper(componentModel = "spring")
public interface BrandRestMapperAdapter extends BrandRestMapper {
}
