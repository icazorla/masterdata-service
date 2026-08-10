package com.ej1.app.product.infrastructure.rest;

import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import org.mapstruct.Mapper;

/**
 * MapStruct-backed implementation of {@link ProductRestMapper}.
 *
 * <p>The {@code @Mapper} annotation lives here (not on the port) so that
 * the generated {@code ProductRestMapperAdapterImpl} is emitted next to its
 * infrastructure neighbours, free of any constraint that would force it into
 * the inbound adapter's package. The resulting bean implements
 * {@link ProductRestMapper} and is registered as a Spring component via
 * {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapperAdapter extends ProductRestMapper {
}
