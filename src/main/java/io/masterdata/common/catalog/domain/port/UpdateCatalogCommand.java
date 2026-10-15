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
 *
 * <p>{@code expectedVersion} es la versión sobre la que el cliente hizo sus cambios: si la entrada
 * ya tiene otra, la actualización se rechaza. {@code null} no comprueba nada.
 */
public record UpdateCatalogCommand(
        Long expectedVersion,
        int weight,
        boolean enabled,
        Map<String, Object> extraFields,
        List<CatalogTranslationCommand> translations
) {
}
