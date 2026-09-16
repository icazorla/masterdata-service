package com.ej1.app.brand.interfaces.rest.mapper;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import com.ej1.app.brand.interfaces.rest.dto.BrandRequest;
import com.ej1.app.brand.interfaces.rest.dto.BrandResponse;

import java.util.List;

/**
 * REST mapper port. Translates between HTTP DTOs ({@link BrandRequest},
 * {@link BrandResponse}) and the domain types ({@link Brand},
 * {@link CreateBrandCommand}, {@link UpdateBrandCommand}).
 */
public interface BrandRestMapper {

    BrandResponse toResponse(Brand brand);

    List<BrandResponse> toResponseList(List<Brand> brands);

    CreateBrandCommand toCreateCommand(BrandRequest request);

    UpdateBrandCommand toUpdateCommand(BrandRequest request);
}
