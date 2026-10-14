package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.port.CatalogMapper;
import org.springframework.stereotype.Component;

/**
 * Adaptador de infraestructura que implementa el puerto de dominio {@link CatalogMapper}.
 * Todos los métodos de {@link CatalogMapper} son métodos default (ver su Javadoc),
 * así que, a diferencia de los antiguos {@code BrandMapperAdapter}/{@code CategoryMapperAdapter},
 * este no necesita una implementación generada por MapStruct — existe únicamente para
 * darle al dominio un bean concreto e inyectable para el puerto.
 */
@Component
public class CatalogMapperAdapter implements CatalogMapper {
}
