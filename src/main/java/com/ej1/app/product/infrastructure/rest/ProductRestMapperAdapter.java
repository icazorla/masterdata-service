package com.ej1.app.product.infrastructure.rest;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.interfaces.rest.dto.ProductResponse;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct-backed implementation of {@link ProductRestMapper}.
 *
 * <p>The {@code @Mapper} annotation lives here (not on the port) so that
 * the generated {@code ProductRestMapperAdapterImpl} is emitted next to its
 * infrastructure neighbours, free of any constraint that would force it into
 * the inbound adapter's package. The resulting bean implements
 * {@link ProductRestMapper} and is registered as a Spring component via
 * {@code componentModel = "spring"}.
 *
 * <p>Every property on {@link com.ej1.app.product.domain.port.ProductSearchCriteria}/
 * {@code ProductRequest} has a same-named counterpart on the other side, so MapStruct's
 * plain field-by-field mapping covers those methods with no overrides needed here.
 * {@code toResponse} is the one exception: {@code ProductResponse.category}/{@code brand}
 * are nested {@code {id, name}} projections built from two separate {@link Product}
 * fields each ({@code categoryId}+{@code categoryName}, {@code brandId}+{@code brandName}),
 * so it's routed to the port's {@link ProductRestMapper#toCategoryRef}/
 * {@link ProductRestMapper#toBrandRef} default methods instead of a 1:1 field match.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapperAdapter extends ProductRestMapper {

    @Override
    @Mapping(target = "category", source = "product")
    @Mapping(target = "brand", source = "product")
    ProductResponse toResponse(Product product);
}
