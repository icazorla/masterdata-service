package io.masterdata.common.catalog.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agregado de datos maestros para cualquier catálogo de name+description
 * discriminado por {@link CatalogType} (comunidades autónomas, provincias, ...). Otros datos lo
 * referencian solo por id.
 *
 * <p>{@code name}/{@code description} se traducen por locale en lugar de ser
 * strings planos: un catálogo lleva un {@link CatalogTranslation} por cada
 * idioma soportado en vez de un único par de idioma fijo. {@code weight} en
 * cambio no se traduce — es un único entero que ordena manualmente las
 * entradas (p. ej. destacar una entrada) independientemente del idioma.
 *
 * <p>{@code enabled} es un interruptor manual: una entrada desactivada deja de ofrecerse
 * en los listados de la API de consulta (los que pueblan selects) pero <em>no</em> se
 * borra ni deja de resolverse por id, porque otros datos pueden seguir referenciándola.
 * Por eso el borrado de un catálogo es lógico: solo lo desactiva (ver {@link #disable()}).
 *
 * <p>{@code extraFields} lleva los campos propios de un {@link CatalogType} concreto
 * (p. ej. {@code regionCode} solo para {@code province} — las columnas propias de su entidad JPA),
 * indexados por nombre de atributo. A diferencia de
 * {@code name}/{@code description}/{@code weight}, este agregado no sabe qué claves
 * puede tener ni de qué tipo son sus valores — eso lo decide únicamente la
 * configuración de cada tipo (ver {@code CatalogTypeDescriptor#extraFields()}), así que
 * aquí es deliberadamente un mapa genérico en vez de campos con nombre propio.
 */
@Getter
public final class Catalog {

    private final Long id;
    private final CatalogType type;
    private final List<CatalogTranslation> translations;
    private final int weight;
    private final boolean enabled;
    private final Map<String, Object> extraFields;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Catalog(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.translations = List.copyOf(b.translations);
        this.weight = b.weight;
        this.enabled = b.enabled;
        // Map.copyOf rechaza valores null; un campo extra ausente/explícitamente
        // vaciado (p. ej. "regionCode": null) es un valor null legítimo, no un error.
        this.extraFields = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraFields));
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
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

    /** Esta misma entrada, desactivada: el resultado de "borrarla" (borrado lógico). */
    public Catalog disable() {
        return toBuilder().enabled(false).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.getId())
                .type(this.type)
                .translations(this.translations)
                .weight(this.weight)
                .enabled(this.enabled)
                .extraFields(this.extraFields)
                .createdAt(this.getCreatedAt())
                .updatedAt(this.getUpdatedAt());
    }

    public static final class Builder {
        private Long id;
        private CatalogType type;
        private List<CatalogTranslation> translations = List.of();
        private int weight = 0;
        private boolean enabled = true;
        private Map<String, Object> extraFields = Map.of();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder type(CatalogType type) { this.type = type; return this; }
        public Builder translations(List<CatalogTranslation> translations) { this.translations = translations; return this; }
        public Builder extraFields(Map<String, Object> extraFields) { this.extraFields = extraFields; return this; }
        public Builder weight(int weight) { this.weight = weight; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Catalog build() {
            return new Catalog(this);
        }
    }
}
