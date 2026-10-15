package io.masterdata.common.catalog.interfaces.rest.mapper;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.CatalogTypeDefinition;
import io.masterdata.common.catalog.domain.port.CatalogTranslationCommand;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogAdminResponse;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogFieldResponse;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogRequest;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogResponse;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogTranslationRequest;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogTranslationResponse;
import io.masterdata.common.catalog.interfaces.rest.dto.CatalogTypeResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mapeo REST. Traduce entre los DTOs HTTP ({@link CatalogRequest},
 * {@link CatalogResponse}) y los tipos de dominio ({@link Catalog},
 * {@link CreateCatalogCommand}, {@link UpdateCatalogCommand}).
 *
 * <p>{@code toResponse}/{@code toResponseList} resuelven el único {@code name}/
 * {@code description} que se muestra a los clientes habituales a partir del
 * conjunto completo de traducciones de la entrada, eligiendo el idioma del
 * {@code Accept-Language} del solicitante y recurriendo a {@link #defaultLocale}
 * cuando ese idioma no tiene traducción — es la respuesta de la API de consulta.
 * {@code toAdminResponse}/{@code toAdminResponseList} exponen en cambio todos los
 * idiomas a la vez (indexados por idioma, igual que
 * {@link CatalogRequest#getTranslations()}), para la API de gestión
 * ({@code /admin/api/catalogs/{type}}).
 *
 * <p>Una sola implementación sirve a todos los {@link CatalogType}: no queda ninguna
 * variación por tipo de catálogo.
 */
@Component
public class CatalogRestMapper {

    /** Idioma de respaldo cuando una entrada no tiene traducción para el del cliente: {@code spring.web.locale}. */
    private final String defaultLocale;

    public CatalogRestMapper(@Value("${spring.web.locale:en}") String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public CatalogResponse toResponse(Catalog catalog, Locale locale) {
        CatalogTranslation t = catalog.resolveTranslation(locale.getLanguage(), defaultLocale);
        return new CatalogResponse(catalog.getId(), t.name(), t.description(), catalog.isEnabled(), catalog.getExtraFields());
    }

    public List<CatalogResponse> toResponseList(List<Catalog> catalogs, Locale locale) {
        return catalogs.stream().map(c -> toResponse(c, locale)).toList();
    }

    public CatalogAdminResponse toAdminResponse(Catalog catalog) {
        return new CatalogAdminResponse(catalog.getId(), catalog.getType(), catalog.getWeight(), catalog.isEnabled(),
                toTranslationResponses(catalog.getTranslations()), catalog.getExtraFields(),
                catalog.getCreatedAt(), catalog.getUpdatedAt());
    }

    public List<CatalogAdminResponse> toAdminResponseList(List<Catalog> catalogs) {
        return catalogs.stream().map(this::toAdminResponse).toList();
    }

    public Map<String, CatalogTranslationResponse> toTranslationResponses(List<CatalogTranslation> translations) {
        return translations.stream().collect(Collectors.toMap(
                CatalogTranslation::locale,
                t -> new CatalogTranslationResponse(t.name(), t.description())));
    }

    /** {@code name} es el nombre ya traducido del tipo — resolverlo necesita el {@code MessageSource}, que este mapeador no tiene. */
    public CatalogTypeResponse toTypeResponse(CatalogTypeDefinition definition, String name) {
        List<CatalogFieldResponse> fields = definition.fields().stream()
                .map(f -> new CatalogFieldResponse(f.name(), f.dataType(), f.required(), f.readOnly(),
                        f.translatable(), f.sortable(), f.extra(), f.filterOperators()))
                .toList();
        return new CatalogTypeResponse(definition.type(), name, fields);
    }

    public CreateCatalogCommand toCreateCommand(CatalogType type, CatalogRequest request) {
        return new CreateCatalogCommand(type, weightOrDefault(request), enabledOrDefault(request), request.getExtraFields(), toCommands(request.getTranslations()));
    }

    public UpdateCatalogCommand toUpdateCommand(CatalogRequest request) {
        return new UpdateCatalogCommand(weightOrDefault(request), enabledOrDefault(request), request.getExtraFields(), toCommands(request.getTranslations()));
    }

    /** {@code weight} es opcional en el payload — {@code null} explícito en el JSON pisaría el valor por defecto del DTO. */
    private static int weightOrDefault(CatalogRequest request) {
        return Objects.requireNonNullElse(request.getWeight(), 0);
    }

    /** Igual que {@link #weightOrDefault}: {@code enabled} es opcional y por defecto {@code true}. */
    private static boolean enabledOrDefault(CatalogRequest request) {
        return Objects.requireNonNullElse(request.getEnabled(), true);
    }

    /** El estado actual como {@link CatalogRequest} — el documento que parchea {@code JsonPatchApplier}. */
    public CatalogRequest toRequest(Catalog catalog) {
        Map<String, CatalogTranslationRequest> translations = catalog.getTranslations().stream()
                .collect(Collectors.toMap(
                        CatalogTranslation::locale,
                        t -> CatalogTranslationRequest.builder().name(t.name()).description(t.description()).build()));
        return CatalogRequest.builder()
                .weight(catalog.getWeight())
                .enabled(catalog.isEnabled())
                .extraFields(new HashMap<>(catalog.getExtraFields()))
                .translations(translations)
                .build();
    }

    private static List<CatalogTranslationCommand> toCommands(Map<String, CatalogTranslationRequest> translations) {
        return translations.entrySet().stream()
                .map(e -> new CatalogTranslationCommand(e.getKey(), e.getValue().getName(), e.getValue().getDescription()))
                .toList();
    }
}
