package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.port.CreateProductCommand;
import com.ej1.app.product.domain.port.ProductMapper;
import com.ej1.app.product.domain.port.UpdateProductCommand;
import com.ej1.app.product.domain.Product;
import org.mapstruct.Mapper;

/**
 * Infrastructure adapter that implements the {@link ProductMapper} domain port.
 *
 * <p>This is where the {@link Mapper} annotation lives (not on the domain interface)
 * so that MapStruct's generated implementation does not depend on classes from the
 * domain runtime — keeping the dependency direction strictly
 * {@code infrastructure → domain}, never the reverse. MapStruct generates the
 * implementation {@code ProductMapperAdapterImpl} in
 * {@code target/generated-sources/annotations/...}, registered as a Spring bean via
 * {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface ProductMapperAdapter extends ProductMapper {
}