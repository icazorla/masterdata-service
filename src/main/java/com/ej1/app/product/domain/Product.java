package com.ej1.app.product.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public final class Product {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final Integer stock;
    private final String sku;
    private final String category;
    private final String brand;
    private final String imageUrl;
    private final BigDecimal weight;
    private final String dimensions;
    private final String tags;
    private final Boolean active;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Product(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.price = b.price;
        this.stock = b.stock;
        this.sku = b.sku;
        this.category = b.category;
        this.brand = b.brand;
        this.imageUrl = b.imageUrl;
        this.weight = b.weight;
        this.dimensions = b.dimensions;
        this.tags = b.tags;
        this.active = b.active == null ? Boolean.TRUE : b.active;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
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
                .category(this.category)
                .brand(this.brand)
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
                .category(this.category)
                .brand(this.brand)
                .imageUrl(this.imageUrl)
                .weight(this.weight)
                .dimensions(this.dimensions)
                .tags(this.tags)
                .active(this.active)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    public static final class Builder {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer stock;
        private String sku;
        private String category;
        private String brand;
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
        public Builder category(String category) { this.category = category; return this; }
        public Builder brand(String brand) { this.brand = brand; return this; }
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