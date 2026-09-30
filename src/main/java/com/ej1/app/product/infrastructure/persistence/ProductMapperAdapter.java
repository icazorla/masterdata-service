package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.product.domain.port.ProductMapper;
import org.mapstruct.Mapper;

/**
 * Adaptador de infraestructura que implementa el puerto de dominio {@link ProductMapper}.
 *
 * <p>Aquí es donde vive la anotación {@link Mapper} (no en la interfaz de dominio)
 * para que la implementación generada por MapStruct no dependa de clases del runtime
 * del dominio — manteniendo la dirección de dependencia estrictamente
 * {@code infrastructure → domain}, nunca al revés. MapStruct genera la
 * implementación {@code ProductMapperAdapterImpl} en
 * {@code target/generated-sources/annotations/...}, registrada como bean de Spring mediante
 * {@code componentModel = "spring"}.
 */
@Mapper(componentModel = "spring")
public interface ProductMapperAdapter extends ProductMapper {
}