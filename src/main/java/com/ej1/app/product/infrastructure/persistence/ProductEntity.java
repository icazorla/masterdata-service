package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.brand.infrastructure.persistence.BrandEntity;
import com.ej1.app.category.infrastructure.persistence.CategoryEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    /**
     * Excluded from {@code equals}/{@code hashCode}/{@code toString} (Lombok's {@code @Data}
     * would otherwise touch this lazy association's proxy outside a Hibernate session, e.g.
     * when logging or asserting on a detached entity, throwing {@code LazyInitializationException}).
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private BrandEntity brand;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(precision = 8, scale = 3)
    private BigDecimal weight;

    @Column(length = 50)
    private String dimensions;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}