package io.masterdata.common.catalog.domain.service;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.port.CatalogTranslationCommand;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;

import java.util.List;

/**
 * Mapea {@link CreateCatalogCommand}/{@link UpdateCatalogCommand} al agregado {@link Catalog}.
 * Solo lo usa {@link CatalogCommandService}.
 */
final class CatalogMapper {

    private CatalogMapper() {
    }

    static Catalog toDomain(CreateCatalogCommand cmd) {
        return Catalog.builder()
                .type(cmd.type())
                .weight(cmd.weight())
                .enabled(cmd.enabled())
                .extraFields(cmd.extraFields())
                .translations(toTranslations(cmd.translations()))
                .build();
    }

    static Catalog applyUpdate(Catalog existing, UpdateCatalogCommand cmd) {
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
