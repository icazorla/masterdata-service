package com.ej1.app.category.domain;

import com.ej1.app.shared.domain.Auditable;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Master-data aggregate for product categories. Referenced from
 * {@code com.ej1.app.product.domain.Product} by id only ({@code categoryId}) —
 * {@code Product} does not hold a {@code Category} reference, keeping the two
 * aggregates independent.
 *
 * <p>{@code name}/{@code description} are translated per locale rather than
 * plain strings: a category carries one {@link CategoryTranslation} per supported
 * language instead of a single hardcoded-language pair.
 */
@Getter
public final class Category extends Auditable {

    private final Long id;
    private final List<CategoryTranslation> translations;

    private Category(Builder b) {
        super(b.createdAt, b.updatedAt);
        this.id = b.id;
        this.translations = List.copyOf(b.translations);
    }

    /** The translation for {@code locale}, if this category has one. */
    public Optional<CategoryTranslation> translation(String locale) {
        return translations.stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst();
    }

    /**
     * The translation for {@code locale}, falling back to {@code defaultLocale}
     * when this category has none for the requested locale. Every category is required
     * to carry a translation for the default locale (enforced on write — see
     * {@code ValidCategoryTranslations}), so this only throws if that invariant was
     * somehow violated.
     */
    public CategoryTranslation resolveTranslation(String locale, String defaultLocale) {
        return translation(locale)
                .or(() -> translation(defaultLocale))
                .orElseThrow(() -> new IllegalStateException("Category " + id + " has no translations"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Category withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
        return builder()
                .id(newId)
                .translations(this.translations)
                .createdAt(created)
                .updatedAt(updated)
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .translations(this.translations)
                .createdAt(this.getCreatedAt())
                .updatedAt(this.getUpdatedAt());
    }

    public static final class Builder {
        private Long id;
        private List<CategoryTranslation> translations = List.of();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder translations(List<CategoryTranslation> translations) { this.translations = translations; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Category build() {
            return new Category(this);
        }
    }
}
