package com.ej1.app.brand.interfaces.rest.mapper;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.BrandTranslation;
import com.ej1.app.brand.domain.port.BrandTranslationCommand;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import com.ej1.app.brand.interfaces.rest.dto.BrandRequest;
import com.ej1.app.brand.interfaces.rest.dto.BrandResponse;
import com.ej1.app.brand.interfaces.rest.dto.BrandTranslationRequest;
import com.ej1.app.brand.interfaces.rest.dto.BrandTranslationResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST mapper port. Translates between HTTP DTOs ({@link BrandRequest},
 * {@link BrandResponse}) and the domain types ({@link Brand},
 * {@link CreateBrandCommand}, {@link UpdateBrandCommand}).
 *
 * <p>{@code toResponse}/{@code toResponseList} resolve the single {@code name}/
 * {@code description} shown to regular clients from the brand's full set of
 * translations, picking the caller's {@code Accept-Language} locale and
 * falling back to {@link #DEFAULT_LOCALE} when that locale has none.
 * {@code toTranslationResponses} exposes every locale at once (keyed by
 * locale, like {@link BrandRequest#getTranslations()}), for the admin-facing
 * {@code GET /api/brands/{id}/translations} endpoint.
 *
 * <p>Every method here is a default method rather than MapStruct-generated:
 * the {@code Map<locale, ...>} <-> {@code List<...Command>} conversions
 * MapStruct can't derive from property names alone, so there's nothing left
 * for it to auto-implement.
 */
public interface BrandRestMapper {

    String DEFAULT_LOCALE = "en";

    default BrandResponse toResponse(Brand brand, Locale locale) {
        BrandTranslation t = brand.resolveTranslation(locale.getLanguage(), DEFAULT_LOCALE);
        return new BrandResponse(brand.getId(), t.name(), t.description(), brand.getCreatedAt(), brand.getUpdatedAt());
    }

    default List<BrandResponse> toResponseList(List<Brand> brands, Locale locale) {
        return brands.stream().map(b -> toResponse(b, locale)).toList();
    }

    default Map<String, BrandTranslationResponse> toTranslationResponses(List<BrandTranslation> translations) {
        return translations.stream().collect(Collectors.toMap(
                BrandTranslation::locale,
                t -> new BrandTranslationResponse(t.name(), t.description())));
    }

    default CreateBrandCommand toCreateCommand(BrandRequest request) {
        return new CreateBrandCommand(toCommands(request.getTranslations()));
    }

    default UpdateBrandCommand toUpdateCommand(BrandRequest request) {
        return new UpdateBrandCommand(toCommands(request.getTranslations()));
    }

    /** The current state as a {@link BrandRequest} — the document {@code JsonPatchApplier} patches. */
    default BrandRequest toRequest(Brand brand) {
        Map<String, BrandTranslationRequest> translations = brand.getTranslations().stream()
                .collect(Collectors.toMap(
                        BrandTranslation::locale,
                        t -> BrandTranslationRequest.builder().name(t.name()).description(t.description()).build()));
        return BrandRequest.builder().translations(translations).build();
    }

    private static List<BrandTranslationCommand> toCommands(Map<String, BrandTranslationRequest> translations) {
        return translations.entrySet().stream()
                .map(e -> new BrandTranslationCommand(e.getKey(), e.getValue().getName(), e.getValue().getDescription()))
                .toList();
    }
}
