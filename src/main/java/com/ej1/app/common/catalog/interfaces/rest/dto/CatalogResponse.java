package com.ej1.app.common.catalog.interfaces.rest.dto;

import com.ej1.app.common.catalog.domain.CatalogType;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * {@code extraFields} son los campos propios de {@code type} (p. ej. {@code website} solo
 * para {@code brand}), servidos como atributos normales de primer nivel del JSON —no
 * anidados bajo una clave {@code "extraFields"}— gracias al accesor sobrescrito más abajo
 * con {@link JsonAnyGetter}; ver {@code CatalogRequest} para el porqué de este mismo
 * mecanismo en el lado de entrada.
 */
public record CatalogResponse(
        Long id,
        CatalogType type,
        String name,
        @Nullable String description,
        int weight,
        Map<String, Object> extraFields,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    @JsonAnyGetter
    @Override
    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
