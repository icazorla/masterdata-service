package com.ej1.app.common.catalog.infrastructure.rest;

import com.ej1.app.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import org.springframework.stereotype.Component;

/**
 * Adaptador de infraestructura que implementa el puerto {@link CatalogRestMapper}
 * orientado al dominio. Todos los métodos de {@link CatalogRestMapper} son métodos
 * default, así que, a diferencia de los antiguos {@code BrandRestMapperAdapter}/
 * {@code CategoryRestMapperAdapter}, este no necesita una implementación generada
 * por MapStruct — existe únicamente para darle al controlador un bean concreto e
 * inyectable para el puerto.
 */
@Component
public class CatalogRestMapperAdapter implements CatalogRestMapper {
}
