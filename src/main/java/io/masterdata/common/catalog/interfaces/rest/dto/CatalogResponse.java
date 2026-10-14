package io.masterdata.common.catalog.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Respuesta de la API de consulta: solo lo que necesita un cliente final para mostrar la
 * entrada. Sin {@code type} (ya va en la ruta), ni {@code weight} (el orden ya viene
 * aplicado por el servidor), ni {@code createdAt}/{@code updatedAt} (auditoría) — todo
 * eso solo se expone en {@link CatalogAdminResponse}. {@code enabled} sí se mantiene: en
 * el listado siempre es {@code true}, pero la consulta por id devuelve también entradas
 * desactivadas y el cliente necesita distinguirlas.
 *
 * <p> * {@code extraFields} son los campos propios de {@code type} (p. ej. {@code website} solo
 * para {@code brand}), servidos como atributos normales de primer nivel del JSON —no
 * anidados bajo una clave {@code "extraFields"}— gracias al accesor sobrescrito más abajo
 * con {@link JsonAnyGetter}; ver {@code CatalogRequest} para el porqué de este mismo
 * mecanismo en el lado de entrada.
 */
public record CatalogResponse(
        Long id,
        String name,
        @Nullable String description,
        boolean enabled,
        Map<String, Object> extraFields
) {
    @JsonAnyGetter
    @Override
    public Map<String, Object> extraFields() {
        return extraFields;
    }
}
