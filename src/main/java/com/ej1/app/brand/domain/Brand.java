package com.ej1.app.brand.domain;

import com.ej1.app.shared.domain.Auditable;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Master-data aggregate for product brands. Referenced from
 * {@code com.ej1.app.product.domain.Product} by id only ({@code brandId}) —
 * {@code Product} does not hold a {@code Brand} reference, keeping the two
 * aggregates independent.
 *
 * <p>{@code name}/{@code description} are translated per locale rather than
 * plain strings: a brand carries one {@link BrandTranslation} per supported
 * language instead of a single hardcoded-language pair.
 */
@Getter
public final class Brand extends Auditable {

    private final Long id;
    private final List<BrandTranslation> translations;

    private Brand(Builder b) {
        super(b.createdAt, b.updatedAt);
        this.id = b.id;
        this.translations = List.copyOf(b.translations);
    }

    /** The translation for {@code locale}, if this brand has one. */
    public Optional<BrandTranslation> translation(String locale) {
        return translations.stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst();
    }

    /**
     * The translation for {@code locale}, falling back to {@code defaultLocale}
     * when this brand has none for the requested locale. Every brand is required
     * to carry a translation for the default locale (enforced on write — see
     * {@code ValidBrandTranslations}), so this only throws if that invariant was
     * somehow violated.
     */
    public BrandTranslation resolveTranslation(String locale, String defaultLocale) {
        return translation(locale)
                .or(() -> translation(defaultLocale))
                .orElseThrow(() -> new IllegalStateException("Brand " + id + " has no translations"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Brand withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
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
        private List<BrandTranslation> translations = List.of();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder translations(List<BrandTranslation> translations) { this.translations = translations; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Brand build() {
            return new Brand(this);
        }
    }
}
