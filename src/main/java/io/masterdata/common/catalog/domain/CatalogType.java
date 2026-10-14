package io.masterdata.common.catalog.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.context.MessageSourceResolvable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Identifica a qué catálogo de datos maestros de name+description pertenece
 * una instancia de {@link Catalog}. Deliberadamente NO es un {@code enum} de
 * Java: el conjunto de tipos de catálogo que existen no es una constante en
 * tiempo de compilación — se toma de {@code app.catalog.types} de
 * {@code application.yml}, que {@code CatalogPersistenceConfig} vuelca aquí
 * mediante {@link #configure(Collection)} al arrancar. Añadir un catálogo
 * significa añadir una entrada de configuración más sus dos clases
 * {@code @Entity}, no tocar esta clase.
 *
 * <p>Se comporta como un {@code enum} cuyos valores vienen de configuración:
 * {@link #of(String)} solo devuelve tipos configurados (y lanza
 * {@link IllegalArgumentException} para cualquier otro) y {@link #values()}
 * lista todos los configurados, en el orden del {@code application.yml}.
 *
 * <p>Implementa {@link MessageSourceResolvable} para que, al pasarse como argumento
 * de un mensaje i18n (p. ej. {@code error.catalog.notFound={0} not found: id={1}}),
 * el {@code MessageSource} lo sustituya por su nombre traducido: la clave
 * {@code catalog.type.<code>}, que sale de {@code app.catalog.types.<code>.names}
 * de {@code application.yml} (ver {@code CatalogTypeNamesMessageSource}) o, si no
 * existe, {@link #label()}. Así un tipo nuevo no necesita ningún mensaje propio.
 */
public final class CatalogType implements MessageSourceResolvable {

    private static volatile Map<String, CatalogType> configured = Map.of();

    private final String code;

    private CatalogType(String code) {
        this.code = code;
    }

    /**
     * Sustituye el conjunto de tipos configurados por {@code codes} (las claves de
     * {@code app.catalog.types}). Lo invoca {@code CatalogPersistenceConfig} al
     * arrancar; no está pensado para llamarse desde código de negocio.
     */
    public static void configure(Collection<String> codes) {
        Map<String, CatalogType> types = new LinkedHashMap<>();
        for (String code : codes) {
            String normalized = normalize(code);
            types.put(normalized, new CatalogType(normalized));
        }
        configured = Collections.unmodifiableMap(types);
    }

    /** Devuelve el {@link CatalogType} configurado cuyo código es {@code code} (trim + minúsculas). Lanza {@link IllegalArgumentException} si no está en {@code app.catalog.types}. */
    public static CatalogType of(String code) {
        Objects.requireNonNull(code, "code");
        CatalogType type = configured.get(normalize(code));
        if (type == null) {
            throw new IllegalArgumentException("Unknown catalog type: " + code);
        }
        return type;
    }

    /** Todos los tipos configurados en {@code app.catalog.types}, en el orden de {@code application.yml}. */
    public static Collection<CatalogType> values() {
        return configured.values();
    }

    private static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Forma en minúsculas usada como segmento de la ruta REST (p. ej.
     * {@code /api/catalogs/brand}), como segmento de la clave de mensaje i18n
     * del nombre traducido (p. ej. {@code catalog.type.brand}), y — mediante {@link JsonValue} —
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

    /** Clave i18n opcional con el nombre traducido del tipo, p. ej. {@code catalog.type.brand}. */
    @Override
    public String[] getCodes() {
        return new String[]{"catalog.type." + code};
    }

    /** Nombre usado cuando no existe {@code catalog.type.<code>} en el bundle: {@link #label()}. */
    @Override
    public String getDefaultMessage() {
        return label();
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
