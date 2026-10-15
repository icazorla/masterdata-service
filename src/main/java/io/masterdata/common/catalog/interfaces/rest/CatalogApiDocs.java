package io.masterdata.common.catalog.interfaces.rest;

/** Textos de OpenAPI que comparten los listados de la API de consulta y la de gestión. */
public final class CatalogApiDocs {

    /** Sintaxis del parámetro {@code filter}; cada API completa después qué campos admite. */
    public static final String FILTER_SYNTAX = "Sintaxis de filter: condiciones campo:valor separadas por ; y "
            + "combinadas todas con AND, p. ej. name:sev;regionCode:1,8;ineCode.gte:10. Varios valores separados "
            + "por , significan «cualquiera de ellos» (texto: contiene alguno, sin distinguir mayúsculas; otros "
            + "tipos: IN). Números y fechas (ISO-8601) admiten rangos con sufijo: campo.gt, campo.gte, campo.lt, "
            + "campo.lte (un único valor). \\; \\, y \\\\ son ; , y \\ literales; los : dentro del valor no "
            + "necesitan escaparse. name/description se evalúan sobre el texto en el idioma de Accept-Language "
            + "(con fallback al idioma por defecto). Campos filtrables: name, description";

    /** Respuesta 422 de un listado. */
    public static final String INVALID_QUERY = "sortBy no es un campo ordenable de catálogo (name, description, "
            + "weight, o un campo adicional de este type), o filter está mal formado, nombra un campo no filtrable "
            + "para este type, usa un operador no válido para su tipo o trae un valor que no encaja con él";

    private CatalogApiDocs() {
    }
}
