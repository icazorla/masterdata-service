package com.ej1.app.category.domain.port;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.CategoryTranslation;

import java.util.List;

/**
 * Domain mapper port. Maps {@link CreateCategoryCommand} / {@link UpdateCategoryCommand}
 * to the {@link Category} aggregate.
 *
 * <p>This interface is part of the domain contract but carries no Spring or MapStruct
 * annotations — those concerns are pushed to the infrastructure adapter
 * ({@code CategoryMapperAdapter}), which implements this port and is bound to Spring.
 */
public interface CategoryMapper {

    Category toDomain(CreateCategoryCommand cmd);

    default Category applyUpdate(Category existing, UpdateCategoryCommand cmd) {
        return existing.toBuilder()
                .translations(toTranslations(cmd.translations()))
                .build();
    }

    private static List<CategoryTranslation> toTranslations(List<CategoryTranslationCommand> commands) {
        return commands.stream()
                .map(c -> new CategoryTranslation(c.locale(), c.name(), c.description()))
                .toList();
    }
}
