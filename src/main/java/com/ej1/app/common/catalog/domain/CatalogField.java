package com.ej1.app.common.catalog.domain;

/**
 * Definición (metadatos, no valor) de un campo de las entradas de un {@link CatalogType}:
 * su nombre en el JSON de la API, su tipo de dato, y cómo se comporta.
 *
 * @param name         nombre del atributo en el payload/respuesta (p. ej. {@code name}, {@code website})
 * @param dataType     tipo de dato JSON-friendly: {@code string}, {@code integer}, {@code number},
 *                     {@code boolean}, {@code date}, {@code date-time}
 * @param required     si debe venir informado al crear/actualizar
 * @param readOnly     si lo asigna el servidor (solo aparece en la respuesta, nunca en el payload)
 * @param translatable si su valor depende del idioma (viaja dentro de {@code translations})
 * @param sortable     si es un valor válido de {@code sortBy} en el listado
 * @param extra        si es un campo adicional propio de este tipo
 *                     ({@code app.catalog.types.<type>.extra-fields}) y no uno común a todo catálogo
 */
public record CatalogField(
        String name,
        String dataType,
        boolean required,
        boolean readOnly,
        boolean translatable,
        boolean sortable,
        boolean extra
) {

    /** Un campo adicional propio del tipo: opcional, editable, no traducido y ordenable. */
    public static CatalogField extra(String name, String dataType) {
        return new CatalogField(name, dataType, false, false, false, true, true);
    }
}
