package io.masterdata.common.catalog.domain.port;

/**
 * Objeto de comando que representa la intención de establecer el {@code name}/
 * {@code description} de un catálogo para un locale. Forma parte de
 * {@link CreateCatalogCommand}/{@link UpdateCatalogCommand}, independiente de
 * cualquier DTO de transporte.
 */
public record CatalogTranslationCommand(String locale, String name, String description) {
}
