package io.masterdata.common.catalog.infrastructure.metrics;

import io.masterdata.common.catalog.domain.CatalogType;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Añade la etiqueta {@code catalog} a {@code http.server.requests}: el {@code uri} de esa métrica es la
 * plantilla de la ruta ({@code /api/catalogs/{type}}), así que sin esto no hay forma de separar las
 * peticiones de cada catálogo en {@code /actuator/metrics/http.server.requests?tag=catalog:province}.
 * Spring Boot recoge este bean como convención de las peticiones del servidor. Siempre hay valor
 * ({@value #NONE} fuera de {@code /{type}}) para que todas las series compartan el mismo juego de etiquetas.
 */
@Component
public class CatalogRequestObservationConvention extends DefaultServerRequestObservationConvention {

    static final String NONE = "none";

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return super.getLowCardinalityKeyValues(context).and("catalog", catalogOf(context.getCarrier()));
    }

    /** Solo un tipo configurado: un valor arbitrario de la ruta crearía una serie nueva por cada petición distinta. */
    private static String catalogOf(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                && variables.get("type") instanceof String type) {
            return CatalogType.values().stream()
                    .map(CatalogType::code)
                    .filter(code -> code.equalsIgnoreCase(type.trim()))
                    .findFirst()
                    .orElse(NONE);
        }
        return NONE;
    }
}
