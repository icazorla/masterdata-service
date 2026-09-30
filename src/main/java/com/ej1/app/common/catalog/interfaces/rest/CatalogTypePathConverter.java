package com.ej1.app.common.catalog.interfaces.rest;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogTypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Permite que los segmentos de ruta {@code {type}} (p. ej. {@code /api/catalogs/brand})
 * se enlacen directamente a {@link CatalogType}, sin distinguir mayúsculas/minúsculas
 * y validados contra el conjunto real de tipos configurados — {@code app.catalog.types}
 * en {@code application.yml} es la lista maestra (ver {@code CatalogPersistenceConfig}),
 * así que a este conversor se le pasa ese mismo registro en vez de parsear contra un
 * conjunto fijo de nombres. Spring Boot lo recoge automáticamente a través de su
 * {@code ConversionService} compartido porque es un bean {@link Converter}; un valor
 * no reconocido lanza {@link IllegalArgumentException}, que Spring MVC expone como una
 * {@code MethodArgumentTypeMismatchException} — ver {@code ApiExceptionHandler#handleTypeMismatch}
 * para la respuesta 400 resultante.
 */
@Component
public class CatalogTypePathConverter implements Converter<String, CatalogType> {

    private final Map<CatalogType, CatalogTypeDescriptor<?, ?>> descriptors;

    public CatalogTypePathConverter(Map<CatalogType, CatalogTypeDescriptor<?, ?>> descriptors) {
        this.descriptors = descriptors;
    }

    @Override
    public CatalogType convert(String source) {
        CatalogType type = CatalogType.of(source);
        if (!descriptors.containsKey(type)) {
            throw new IllegalArgumentException("Unknown catalog type: " + source);
        }
        return type;
    }
}
