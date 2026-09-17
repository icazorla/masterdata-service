package com.ej1.app.category.interfaces.rest.mapper;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.CategoryTranslation;
import com.ej1.app.category.domain.port.CategoryTranslationCommand;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import com.ej1.app.category.interfaces.rest.dto.CategoryRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryResponse;
import com.ej1.app.category.interfaces.rest.dto.CategoryTranslationRequest;
import com.ej1.app.category.interfaces.rest.dto.CategoryTranslationResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST mapper port. Translates between HTTP DTOs ({@link CategoryRequest},
 * {@link CategoryResponse}) and the domain types ({@link Category},
 * {@link CreateCategoryCommand}, {@link UpdateCategoryCommand}).
 *
 * <p>{@code toResponse}/{@code toResponseList} resolve the single {@code name}/
 * {@code description} shown to regular clients from the category's full set of
 * translations, picking the caller's {@code Accept-Language} locale and
 * falling back to {@link #DEFAULT_LOCALE} when that locale has none.
 * {@code toTranslationResponses} exposes every locale at once (keyed by
 * locale, like {@link CategoryRequest#getTranslations()}), for the admin-facing
 * {@code GET /api/categories/{id}/translations} endpoint.
 *
 * <p>Every method here is a default method rather than MapStruct-generated:
 * the {@code Map<locale, ...>} <-> {@code List<...Command>} conversions
 * MapStruct can't derive from property names alone, so there's nothing left
 * for it to auto-implement.
 */
public interface CategoryRestMapper {

    String DEFAULT_LOCALE = "en";

    default CategoryResponse toResponse(Category category, Locale locale) {
        CategoryTranslation t = category.resolveTranslation(locale.getLanguage(), DEFAULT_LOCALE);
        return new CategoryResponse(category.getId(), t.name(), t.description(), category.getCreatedAt(), category.getUpdatedAt());
    }

    default List<CategoryResponse> toResponseList(List<Category> categories, Locale locale) {
        return categories.stream().map(c -> toResponse(c, locale)).toList();
    }

    default Map<String, CategoryTranslationResponse> toTranslationResponses(List<CategoryTranslation> translations) {
        return translations.stream().collect(Collectors.toMap(
                CategoryTranslation::locale,
                t -> new CategoryTranslationResponse(t.name(), t.description())));
    }

    default CreateCategoryCommand toCreateCommand(CategoryRequest request) {
        return new CreateCategoryCommand(toCommands(request.getTranslations()));
    }

    default UpdateCategoryCommand toUpdateCommand(CategoryRequest request) {
        return new UpdateCategoryCommand(toCommands(request.getTranslations()));
    }

    /** The current state as a {@link CategoryRequest} — the document {@code JsonPatchApplier} patches. */
    default CategoryRequest toRequest(Category category) {
        Map<String, CategoryTranslationRequest> translations = category.getTranslations().stream()
                .collect(Collectors.toMap(
                        CategoryTranslation::locale,
                        t -> CategoryTranslationRequest.builder().name(t.name()).description(t.description()).build()));
        return CategoryRequest.builder().translations(translations).build();
    }

    private static List<CategoryTranslationCommand> toCommands(Map<String, CategoryTranslationRequest> translations) {
        return translations.entrySet().stream()
                .map(e -> new CategoryTranslationCommand(e.getKey(), e.getValue().getName(), e.getValue().getDescription()))
                .toList();
    }
}
