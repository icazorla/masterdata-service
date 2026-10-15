package io.masterdata.common.catalog.domain.port;

import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Una condición de filtrado del listado de un catálogo, sobre un campo común filtrable
 * ({@link #COMMON_FIELDS}: {@code name}, {@code description}, {@code enabled}) o sobre un campo
 * adicional propio del tipo (p. ej. {@code regionCode} para {@code province}).
 *
 * <p>Llegan todas juntas en el parámetro {@code filter} del listado, con esta sintaxis (ver
 * {@link #parse}):
 *
 * <pre>
 * filter=name:sev;regionCode:1,8;ineCode.gte:10;ineCode.lt:30
 * </pre>
 *
 * <ul>
 *   <li>Las condiciones se separan con {@code ;} y se combinan todas con AND.</li>
 *   <li>Cada condición es {@code campo:valor} ({@link Operator#EQ}) o {@code campo.op:valor}, con
 *       {@code op} uno de {@code gt}, {@code gte}, {@code lt}, {@code lte} (solo para números y
 *       fechas, un único valor).</li>
 *   <li>Con {@link Operator#EQ}, varios valores separados por {@code ,} significan "cualquiera de
 *       ellos": en texto, contiene alguno (sin distinguir mayúsculas); en el resto, {@code IN}.</li>
 *   <li>{@code \;}, {@code \,} y {@code \\} son un {@code ;}, una {@code ,} y una {@code \}
 *       literales. Los {@code :} dentro del valor no necesitan escaparse (solo cuenta el primero).</li>
 *   <li>Las condiciones y los valores vacíos se ignoran.</li>
 * </ul>
 *
 * <p>Aquí solo se valida la sintaxis. Que {@code field} sea un campo filtrable del tipo consultado
 * y que {@code operator} tenga sentido para su tipo de dato lo comprueba
 * {@code CatalogQueryService}; convertir {@code values} al tipo Java real de la columna es cosa
 * del adaptador de persistencia.
 */
public record CatalogFilter(String field, Operator operator, List<String> values) {

    /** Campos comunes a todo catálogo por los que se puede filtrar. */
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ENABLED = "enabled";
    public static final Set<String> COMMON_FIELDS = Set.of(NAME, DESCRIPTION, ENABLED);

    public CatalogFilter {
        values = List.copyOf(values);
    }

    public CatalogFilter(String field, String... values) {
        this(field, Operator.EQ, List.of(values));
    }

    public enum Operator {
        EQ(null), GT("gt"), GTE("gte"), LT("lt"), LTE("lte");

        private final String suffix;

        Operator(String suffix) {
            this.suffix = suffix;
        }

        /** Sufijo tras el nombre del campo ({@code ineCode.gte}); {@code null} para {@link #EQ}. */
        public String suffix() {
            return suffix;
        }

        public boolean isRange() {
            return this != EQ;
        }

        /**
         * Nombres de los operadores admitidos para un campo de tipo {@code dataType}, tal y como se
         * publican en la definición del tipo: {@code contains} para texto, {@code eq} para
         * booleanos y {@code eq}/{@code gt}/{@code gte}/{@code lt}/{@code lte} para números y
         * fechas. {@code contains} y {@code eq} admiten varios valores separados por comas.
         */
        public static List<String> supportedNames(String dataType) {
            if ("string".equals(dataType)) {
                return List.of("contains");
            }
            if (isOrdered(dataType)) {
                return List.of("eq", "gt", "gte", "lt", "lte");
            }
            return List.of("eq");
        }

        /** Si este operador se puede aplicar a un campo de tipo {@code dataType}. */
        public boolean supports(String dataType) {
            return !isRange() || isOrdered(dataType);
        }

        private static boolean isOrdered(String dataType) {
            return switch (dataType) {
                case "integer", "number", "date", "date-time" -> true;
                default -> false;
            };
        }

        static Operator fromSuffix(String key, String suffix) {
            return Arrays.stream(values())
                    .filter(Operator::isRange)
                    .filter(op -> op.suffix.equals(suffix))
                    .findFirst()
                    .orElseThrow(() -> InvalidCatalogQueryException.unknownFilterOperator(key, suffix));
        }
    }

    /** La clave tal y como se escribe en {@code filter}: {@code campo} o {@code campo.op}. */
    public String key() {
        return operator.isRange() ? field + '.' + operator.suffix() : field;
    }

    /**
     * Las condiciones de una expresión {@code filter} (ver la sintaxis arriba), en el orden escrito.
     * {@code null} o vacía no filtra. Lanza {@link InvalidCatalogQueryException} si una condición no
     * tiene {@code :}, si un sufijo no es un operador conocido o si un operador de rango recibe más
     * de un valor.
     */
    public static List<CatalogFilter> parse(String expression) {
        List<CatalogFilter> filters = new ArrayList<>();
        if (expression == null) {
            return filters;
        }
        for (String condition : splitEscaped(expression, ';')) {
            if (condition.isBlank()) {
                continue;
            }
            int colon = indexOfUnescaped(condition, ':');
            if (colon < 0) {
                throw InvalidCatalogQueryException.malformedFilter(unescape(condition.trim()));
            }
            String key = unescape(condition.substring(0, colon)).trim();
            List<String> values = splitEscaped(condition.substring(colon + 1), ',').stream()
                    .map(v -> unescape(v).trim())
                    .filter(v -> !v.isEmpty())
                    .toList();
            if (key.isEmpty()) {
                throw InvalidCatalogQueryException.malformedFilter(unescape(condition.trim()));
            }
            if (values.isEmpty()) {
                continue;
            }
            int dot = key.indexOf('.');
            String field = dot < 0 ? key : key.substring(0, dot);
            Operator operator = dot < 0 ? Operator.EQ : Operator.fromSuffix(key, key.substring(dot + 1));
            if (operator.isRange() && values.size() > 1) {
                throw InvalidCatalogQueryException.multipleRangeValues(key);
            }
            filters.add(new CatalogFilter(field, operator, values));
        }
        return List.copyOf(filters);
    }

    /** Parte {@code s} por cada {@code separator} no escapado, conservando las secuencias de escape. */
    private static List<String> splitEscaped(String s, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                current.append(c).append(s.charAt(++i));
            } else if (c == separator) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static int indexOfUnescaped(String s, char target) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == target) {
                return i;
            }
        }
        return -1;
    }

    /** {@code \x} pasa a {@code x}; una {@code \} final suelta se conserva. */
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                out.append(s.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
