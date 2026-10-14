package io.masterdata.common.catalog.domain.port;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;

import java.util.List;

/**
 * Puerto del mapper de dominio. Mapea {@link CreateCatalogCommand}/
 * {@link UpdateCatalogCommand} al agregado {@link Catalog}.
 *
 * <p>A diferencia del antiguo {@code BrandMapper}/{@code CategoryMapper} por
 * agregado, aquí todos los métodos pueden ser métodos default: construir un
 * {@link Catalog} a partir de un comando no necesita nada más allá del propio
 * {@code type}/{@code translations} del comando — no queda ninguna variación
 * por tipo de catálogo que delegar a un adaptador de infraestructura/MapStruct.
 */
public interface CatalogMapper {

    default Catalog toDomain(CreateCatalogCommand cmd) {
        return Catalog.builder()
                .type(cmd.type())
                .weight(cmd.weight())
                .enabled(cmd.enabled())
                .extraFields(cmd.extraFields())
                .translations(toTranslations(cmd.translations()))
                .build();
    }

    default Catalog applyUpdate(Catalog existing, UpdateCatalogCommand cmd) {
        return existing.toBuilder()
                .weight(cmd.weight())
                .enabled(cmd.enabled())
                .extraFields(cmd.extraFields())
                .translations(toTranslations(cmd.translations()))
                .build();
    }

    private static List<CatalogTranslation> toTranslations(List<CatalogTranslationCommand> commands) {
        return commands.stream()
                .map(c -> new CatalogTranslation(c.locale(), c.name(), c.description()))
                .toList();
    }
}
