package com.ej1.app.brand.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A {@link BrandEntity}'s {@code name}/{@code description} for one locale.
 * {@code (locale, name)} is unique across every brand, not just within one —
 * mirroring the single global unique {@code name} the pre-i18n {@code BrandEntity}
 * used to enforce, just scoped per language now.
 */
@Entity
@Table(name = "brand_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"locale", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandTranslationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side of the association ({@code brand_id} FK lives here). Excluded from
     * {@code equals}/{@code hashCode}/{@code toString} to avoid recursing back into
     * {@link BrandEntity#getTranslations()}.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;
}
