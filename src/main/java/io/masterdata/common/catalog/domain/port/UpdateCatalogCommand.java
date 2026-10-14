package io.masterdata.common.catalog.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Objeto de comando que representa la intención de actualizar una entrada de
 * catálogo existente. Se mantiene separado de {@link CreateCatalogCommand}
 * para que la firma del caso de uso sea explícita y el contrato de las
 * actualizaciones pueda evolucionar de forma independiente. Qué catálogo (id)
 * y de qué {@code CatalogType} se está actualizando se proporciona junto a
 * este comando, no dentro de él — una actualización nunca cambia el tipo de
 * un catálogo.
 *
 * <p>{@code extraFields} sobrescribe por completo los campos propios del tipo, igual
 * que {@code weight}/{@code enabled}/{@code translations} — no se fusiona con lo ya persistido.
 */
public record UpdateCatalogCommand(
        int weight,
        boolean enabled,
        Map<String, Object> extraFields,
        List<CatalogTranslationCommand> translations
) {
}
