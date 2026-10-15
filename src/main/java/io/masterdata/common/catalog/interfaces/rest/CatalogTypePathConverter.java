package io.masterdata.common.catalog.interfaces.rest;

import io.masterdata.common.catalog.domain.CatalogType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Permite que los segmentos de ruta {@code {type}} (p. ej. {@code /api/catalogs/province})
 * se enlacen directamente a {@link CatalogType}, sin distinguir mayúsculas/minúsculas
 * y validados contra el conjunto real de tipos configurados — {@link CatalogType#of}
 * solo acepta las claves de {@code app.catalog.types} en {@code application.yml} (ver
 * {@code CatalogPersistenceConfig}). Spring Boot lo recoge automáticamente a través de
 * su {@code ConversionService} compartido porque es un bean {@link Converter}; un valor
 * no reconocido lanza {@link IllegalArgumentException}, que Spring MVC expone como una
 * {@code MethodArgumentTypeMismatchException} — ver {@code ApiExceptionHandler#handleTypeMismatch}
 * para la respuesta 400 resultante.
 */
@Component
public class CatalogTypePathConverter implements Converter<String, CatalogType> {

    @Override
    public CatalogType convert(String source) {
        return CatalogType.of(source);
    }
}
