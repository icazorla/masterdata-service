package com.ej1.app.common.domain;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Valida que un valor de {@code sortBy} nombre un campo realmente ordenable, lanzando la
 * excepción propia del llamante cuando no es así. Java puro, sin dependencia de framework,
 * de forma que cualquier servicio de dominio de cualquier feature pueda usarlo sin romper la
 * regla de que "el dominio no sabe nada de frameworks".
 *
 * <p>Deliberadamente <em>no</em> posee el conjunto de campos permitidos ni el tipo de excepción:
 * qué campos son ordenables es conocimiento específico de cada agregado (p. ej. {@code Product}),
 * y qué excepción se mapea a qué estado HTTP es una decisión que ya codifica la propia excepción
 * de dominio de cada feature (ver p. ej. {@code InvalidProductQueryException}). Esta clase solo
 * comparte la mecánica repetitiva — comprobar pertenencia, formatear un mensaje consistente, lanzar —
 * de forma que el servicio de una segunda entidad reutilice esto en lugar de copiar y pegar el mismo {@code if}.
 */
public final class SortableFieldValidator {

    private SortableFieldValidator() {
    }

    /**
     * Lanza {@code exceptionFactory.apply(sortBy, allowedFields)} cuando {@code sortBy} no está en
     * {@code allowedFields}. No hace nada en caso contrario. {@code sortBy} y {@code allowedFields} se
     * pasan como argumentos estructurados (en lugar de preformateados en un mensaje) para que la
     * excepción pueda llevarlos consigo para una localización posterior.
     */
    public static void requireSortable(String sortBy, Set<String> allowedFields,
                                        BiFunction<String, Set<String>, ? extends RuntimeException> exceptionFactory) {
        if (!allowedFields.contains(sortBy)) {
            throw exceptionFactory.apply(sortBy, allowedFields);
        }
    }
}
