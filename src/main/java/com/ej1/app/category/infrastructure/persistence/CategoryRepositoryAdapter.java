package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.CategoryTranslation;
import com.ej1.app.category.domain.port.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final CategoryEntityMapper mapper;

    @Value("${spring.web.locale:en}")
    private String defaultLocale;

    @Override
    public Optional<Category> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCase(String locale, String name) {
        return jpa.existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCase(locale, name);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCaseAndIdNot(String locale, String name, Long excludeId) {
        return jpa.existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCaseAndIdNot(locale, name, excludeId);
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAllOrderByTranslatedName(defaultLocale).stream().map(mapper::toDomain).toList();
    }

    /**
     * On create, {@code mapper.toEntity} builds the whole entity (translations
     * included) from scratch. On update, translations are merged in-place onto
     * the already-persisted collection (see {@link #mergeTranslations}) instead
     * of being replaced wholesale — swapping out a persisted row for a brand-new
     * one with the same {@code (locale, name)} would violate that pair's unique
     * constraint the moment both exist, even transiently, within the same flush.
     */
    @Override
    public Category save(Category category) {
        CategoryEntity entity = category.getId() == null ? mapper.toEntity(category) : mergeTranslations(category);
        return mapper.toDomain(jpa.save(entity));
    }

    private CategoryEntity mergeTranslations(Category category) {
        CategoryEntity existing = jpa.findById(category.getId())
                .orElseThrow(() -> new IllegalStateException("Category " + category.getId() + " not found"));

        Map<String, CategoryTranslationEntity> byLocale = existing.getTranslations().stream()
                .collect(Collectors.toMap(CategoryTranslationEntity::getLocale, t -> t));
        Set<String> incomingLocales = category.getTranslations().stream()
                .map(CategoryTranslation::locale)
                .collect(Collectors.toSet());

        existing.getTranslations().removeIf(t -> !incomingLocales.contains(t.getLocale()));

        for (CategoryTranslation t : category.getTranslations()) {
            CategoryTranslationEntity row = byLocale.get(t.locale());
            if (row != null) {
                row.setName(t.name());
                row.setDescription(t.description());
            } else {
                existing.getTranslations().add(CategoryTranslationEntity.builder()
                        .category(existing)
                        .locale(t.locale())
                        .name(t.name())
                        .description(t.description())
                        .build());
            }
        }
        return existing;
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
