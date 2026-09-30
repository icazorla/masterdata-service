package com.ej1.app.common.catalog.domain;

import com.ej1.app.common.domain.Auditable;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agregado de datos maestros para cualquier catálogo de name+description
 * discriminado por {@link CatalogType} (marcas, categorías, ...). Se referencia
 * desde {@code com.ej1.app.product.domain.Product} solo por id ({@code brandId}/
 * {@code categoryId}) — {@code Product} no mantiene una referencia a
 * {@code Catalog}, manteniendo los agregados independientes.
 *
 * <p>{@code name}/{@code description} se traducen por locale en lugar de ser
 * strings planos: un catálogo lleva un {@link CatalogTranslation} por cada
 * idioma soportado en vez de un único par de idioma fijo. {@code weight} en
 * cambio no se traduce — es un único entero que ordena manualmente las
 * entradas (p. ej. destacar una marca) independientemente del idioma.
 *
 * <p>{@code extraFields} lleva los campos propios de un {@link CatalogType} concreto
 * (p. ej. {@code website} solo para {@code brand} — ver {@code app.catalog.types.brand.extra-fields}
 * en {@code application.yml}), indexados por nombre de atributo. A diferencia de
 * {@code name}/{@code description}/{@code weight}, este agregado no sabe qué claves
 * puede tener ni de qué tipo son sus valores — eso lo decide únicamente la
 * configuración de cada tipo (ver {@code CatalogTypeDescriptor#extraFields()}), así que
 * aquí es deliberadamente un mapa genérico en vez de campos con nombre propio.
 */
@Getter
public final class Catalog extends Auditable {

    private final CatalogType type;
    private final List<CatalogTranslation> translations;
    private final int weight;
    private final Map<String, Object> extraFields;

    private Catalog(Builder b) {
        super(b.id, b.createdAt, b.updatedAt);
        this.type = b.type;
        this.translations = List.copyOf(b.translations);
        this.weight = b.weight;
        // Map.copyOf rechaza valores null; un campo extra ausente/explícitamente
        // vaciado (p. ej. "website": null) es un valor null legítimo, no un error.
        this.extraFields = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraFields));
    }

    /** La traducción para {@code locale}, si este catálogo tiene una. */
    public Optional<CatalogTranslation> translation(String locale) {
        return translations.stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst();
    }

    /**
     * La traducción para {@code locale}, recurriendo a {@code defaultLocale}
     * cuando este catálogo no tiene ninguna para el locale solicitado. Todo
     * catálogo está obligado a llevar una traducción para el locale por
     * defecto (impuesto en la escritura — ver {@code ValidCatalogTranslations}),
     * así que esto solo lanza excepción si de algún modo se violó ese
     * invariante.
     */
    public CatalogTranslation resolveTranslation(String locale, String defaultLocale) {
        return translation(locale)
                .or(() -> translation(defaultLocale))
                .orElseThrow(() -> new IllegalStateException(type + " " + getId() + " has no translations"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Catalog withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated) {
        return builder()
                .id(newId)
                .type(this.type)
                .translations(this.translations)
                .weight(this.weight)
                .extraFields(this.extraFields)
                .createdAt(created)
                .updatedAt(updated)
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.getId())
                .type(this.type)
                .translations(this.translations)
                .weight(this.weight)
                .extraFields(this.extraFields)
                .createdAt(this.getCreatedAt())
                .updatedAt(this.getUpdatedAt());
    }

    public static final class Builder {
        private Long id;
        private CatalogType type;
        private List<CatalogTranslation> translations = List.of();
        private int weight = 0;
        private Map<String, Object> extraFields = Map.of();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder type(CatalogType type) { this.type = type; return this; }
        public Builder translations(List<CatalogTranslation> translations) { this.translations = translations; return this; }
        public Builder extraFields(Map<String, Object> extraFields) { this.extraFields = extraFields; return this; }
        public Builder weight(int weight) { this.weight = weight; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Catalog build() {
            return new Catalog(this);
        }
    }
}
