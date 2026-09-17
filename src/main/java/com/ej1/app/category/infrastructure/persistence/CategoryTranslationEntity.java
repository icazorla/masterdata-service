package com.ej1.app.category.infrastructure.persistence;

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
 * A {@link CategoryEntity}'s {@code name}/{@code description} for one locale.
 * {@code (locale, name)} is unique across every category, not just within one —
 * mirroring the single global unique {@code name} the pre-i18n {@code CategoryEntity}
 * used to enforce, just scoped per language now.
 */
@Entity
@Table(name = "category_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"locale", "name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTranslationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side of the association ({@code category_id} FK lives here). Excluded from
     * {@code equals}/{@code hashCode}/{@code toString} to avoid recursing back into
     * {@link CategoryEntity#getTranslations()}.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;
}
