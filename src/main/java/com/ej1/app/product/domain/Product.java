package com.ej1.app.product.domain;

import com.ej1.app.shared.domain.Auditable;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code categoryId}/{@code brandId} are the real reference — the only fields commands
 * write and search filters match on, per the DDD convention of referencing another
 * aggregate by identity only. {@code categoryName}/{@code brandName} are a read-only,
 * denormalized label carried alongside for display: populated by the persistence adapter
 * when reading (via the {@code ProductEntity} association, fetched with the row in one
 * query — see {@code ProductEntityMapper}), always {@code null} on a freshly-built command
 * draft, and ignored by {@code ProductMapper} (commands never set it).
 */
@Getter
public final class Product extends Auditable {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final Integer stock;
    private final String sku;
    private final Long categoryId;
    private final String categoryName;
    private final Long brandId;
    private final String brandName;
    private final String imageUrl;
    private final BigDecimal weight;
    private final String dimensions;
    private final String tags;
    private final Boolean active;

    private Product(Builder b) {
        super(b.createdAt, b.updatedAt);
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.price = b.price;
        this.stock = b.stock;
        this.sku = b.sku;
        this.categoryId = b.categoryId;
        this.categoryName = b.categoryName;
        this.brandId = b.brandId;
        this.brandName = b.brandName;
        this.imageUrl = b.imageUrl;
        this.weight = b.weight;
        this.dimensions = b.dimensions;
        this.tags = b.tags;
        this.active = b.active == null ? Boolean.TRUE : b.active;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Product withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
        return builder()
                .id(newId)
                .name(this.name)
                .description(this.description)
                .price(this.price)
                .stock(this.stock)
                .sku(this.sku)
                .categoryId(this.categoryId)
                .categoryName(this.categoryName)
                .brandId(this.brandId)
                .brandName(this.brandName)
                .imageUrl(this.imageUrl)
                .weight(this.weight)
                .dimensions(this.dimensions)
                .tags(this.tags)
                .active(this.active)
                .createdAt(created)
                .updatedAt(updated)
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .price(this.price)
                .stock(this.stock)
                .sku(this.sku)
                .categoryId(this.categoryId)
                .categoryName(this.categoryName)
                .brandId(this.brandId)
                .brandName(this.brandName)
                .imageUrl(this.imageUrl)
                .weight(this.weight)
                .dimensions(this.dimensions)
                .tags(this.tags)
                .active(this.active)
                .createdAt(this.getCreatedAt())
                .updatedAt(this.getUpdatedAt());
    }

    public static final class Builder {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer stock;
        private String sku;
        private Long categoryId;
        private String categoryName;
        private Long brandId;
        private String brandName;
        private String imageUrl;
        private BigDecimal weight;
        private String dimensions;
        private String tags;
        private Boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder stock(Integer stock) { this.stock = stock; return this; }
        public Builder sku(String sku) { this.sku = sku; return this; }
        public Builder categoryId(Long categoryId) { this.categoryId = categoryId; return this; }
        public Builder categoryName(String categoryName) { this.categoryName = categoryName; return this; }
        public Builder brandId(Long brandId) { this.brandId = brandId; return this; }
        public Builder brandName(String brandName) { this.brandName = brandName; return this; }
        public Builder imageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder weight(BigDecimal weight) { this.weight = weight; return this; }
        public Builder dimensions(String dimensions) { this.dimensions = dimensions; return this; }
        public Builder tags(String tags) { this.tags = tags; return this; }
        public Builder active(Boolean active) { this.active = active; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Product build() {
            return new Product(this);
        }
    }
}