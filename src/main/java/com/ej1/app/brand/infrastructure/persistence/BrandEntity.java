package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brands")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code EAGER}: master data, a handful of brands with a couple of locales
     * each, so the extra selects are cheap and this avoids the
     * {@code LazyInitializationException} risk of returning a detached
     * {@code Brand} whose translations were never fetched.
     *
     * <p>{@code save()} always replaces this list wholesale (see
     * {@code BrandEntityMapper#toEntity}) rather than patching individual rows —
     * with {@code orphanRemoval = true} that reliably deletes translations no
     * longer present and inserts the new ones, at the cost of new ids being
     * assigned on every update. Fine here: translation ids are never exposed
     * outside this adapter.
     */
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<BrandTranslationEntity> translations = new ArrayList<>();
}
