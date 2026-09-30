package com.ej1.app.common.catalog.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifica a qué catálogo de datos maestros de name+description pertenece
 * una instancia de {@link Catalog}. Deliberadamente NO es un {@code enum} de
 * Java: el conjunto de tipos de catálogo que existen no es una constante en
 * tiempo de compilación — {@code app.catalog.types} de {@code application.yml}
 * es la lista maestra (ver {@code CatalogPersistenceConfig}/
 * {@code CatalogProperties}), así que añadir un catálogo significa añadir una
 * entrada de configuración más sus dos clases {@code @Entity}, no un nuevo
 * valor aquí.
 *
 * <p>{@link #BRAND}/{@link #CATEGORY} de abajo son constantes de conveniencia
 * para los dos tipos de los que {@code com.ej1.app.product.domain.Product}
 * depende estructuralmente (sus columnas {@code brandId}/{@code categoryId})
 * — no tienen ningún estatus especial, son simplemente valores de
 * {@link CatalogType} ya construidos, iguales a {@code CatalogType.of("brand")}/
 * {@code CatalogType.of("category")}. Si un {@link CatalogType} dado existe
 * realmente lo decide únicamente si es una clave en el registro de
 * descriptores configurado, no nada de esta clase.
 */
public final class CatalogType {

    public static final CatalogType BRAND = new CatalogType("brand");
    public static final CatalogType CATEGORY = new CatalogType("category");

    private final String code;

    private CatalogType(String code) {
        this.code = code;
    }

    /** Normaliza {@code code} (trim + minúsculas) en un {@link CatalogType}. Si realmente es un tipo configurado le corresponde comprobarlo al llamante contra el registro — esto solo construye el identificador. */
    public static CatalogType of(String code) {
        Objects.requireNonNull(code, "code");
        return new CatalogType(code.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Forma en minúsculas usada como segmento de la ruta REST (p. ej.
     * {@code /api/catalogs/brand}), como segmento de la clave de mensaje i18n
     * (p. ej. {@code error.brand.notFound}), y — mediante {@link JsonValue} —
     * como representación JSON de este tipo (un string plano, p. ej.
     * {@code "brand"}, en lugar de {@code {}}: sin un {@code enum} al que
     * Jackson pueda recurrir y sin un {@code getCode()} al estilo JavaBean, de
     * otro modo no vería ninguna propiedad serializable en absoluto).
     */
    @JsonValue
    public String code() {
        return code;
    }

    /** {@link #code()}, capitalizado — usado en mensajes de excepción orientados al desarrollador (en inglés, de log/fallback), p. ej. {@code "Brand not found: id=1"}. */
    public String label() {
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CatalogType other && code.equals(other.code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
