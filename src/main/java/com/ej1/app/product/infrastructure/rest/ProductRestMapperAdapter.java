package com.ej1.app.product.infrastructure.rest;

import com.ej1.app.product.domain.Product;
import com.ej1.app.product.interfaces.rest.dto.ProductResponse;
import com.ej1.app.product.interfaces.rest.mapper.ProductRestMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Implementación respaldada por MapStruct de {@link ProductRestMapper}.
 *
 * <p>La anotación {@code @Mapper} vive aquí (no en el puerto) para que el
 * {@code ProductRestMapperAdapterImpl} generado se emita junto a sus vecinos de
 * infraestructura, libre de cualquier restricción que lo forzara al paquete del
 * adaptador de entrada. El bean resultante implementa
 * {@link ProductRestMapper} y se registra como componente de Spring mediante
 * {@code componentModel = "spring"}.
 *
 * <p>Cada propiedad de {@link com.ej1.app.product.domain.port.ProductSearchCriteria}/
 * {@code ProductRequest} tiene una contraparte con el mismo nombre en el otro lado, así
 * que el mapeo simple campo a campo de MapStruct cubre esos métodos sin necesidad de
 * overrides aquí. {@code toResponse} es la única excepción: {@code ProductResponse.category}/
 * {@code brand} son proyecciones anidadas {@code {id, name}} construidas a partir de dos
 * campos separados de {@link Product} cada una ({@code categoryId}+{@code categoryName},
 * {@code brandId}+{@code brandName}), así que se enruta a los métodos default del puerto
 * {@link ProductRestMapper#toCategoryRef}/{@link ProductRestMapper#toBrandRef} en lugar
 * de una correspondencia 1:1 de campos.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapperAdapter extends ProductRestMapper {

    @Override
    @Mapping(target = "category", source = "product")
    @Mapping(target = "brand", source = "product")
    ProductResponse toResponse(Product product);
}
