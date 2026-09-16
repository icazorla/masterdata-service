package com.ej1.app.brand.domain;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Master-data aggregate for product brands. Referenced from
 * {@code com.ej1.app.product.domain.Product} by id only ({@code brandId}) —
 * {@code Product} does not hold a {@code Brand} reference, keeping the two
 * aggregates independent.
 */
@Getter
public final class Brand {

    private final Long id;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Brand(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Brand withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
        return builder()
                .id(newId)
                .name(this.name)
                .description(this.description)
                .createdAt(created)
                .updatedAt(updated)
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    public static final class Builder {
        private Long id;
        private String name;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Brand build() {
            return new Brand(this);
        }
    }
}
