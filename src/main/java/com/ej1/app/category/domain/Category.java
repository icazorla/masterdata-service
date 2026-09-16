package com.ej1.app.category.domain;

import com.ej1.app.shared.domain.Auditable;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Master-data aggregate for product categories. Referenced from
 * {@code com.ej1.app.product.domain.Product} by id only ({@code categoryId}) —
 * {@code Product} does not hold a {@code Category} reference, keeping the two
 * aggregates independent.
 */
@Getter
public final class Category extends Auditable {

    private final Long id;
    private final String name;
    private final String description;

    private Category(Builder b) {
        super(b.createdAt, b.updatedAt);
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Category withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
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
                .createdAt(this.getCreatedAt())
                .updatedAt(this.getUpdatedAt());
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

        public Category build() {
            return new Category(this);
        }
    }
}
