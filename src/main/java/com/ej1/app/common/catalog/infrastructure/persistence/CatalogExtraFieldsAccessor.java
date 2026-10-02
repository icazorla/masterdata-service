package com.ej1.app.common.catalog.infrastructure.persistence;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee/escribe, por reflexión, los campos adicionales propios de un tipo de catálogo
 * concreto ({@code CatalogTypeDescriptor#extraFields()} — solo nombres, p. ej.
 * {@code "website"} para {@code brand}) contra el getter/setter de JavaBean real de esa
 * entidad ({@code BrandEntity#getWebsite}/{@code #setWebsite}, generados por Lombok
 * {@code @Data}). Existe para que {@link CatalogRepositoryAdapter} —agnóstico de qué
 * campos extra tiene cada tipo— pueda copiarlos entre la entidad y el
 * {@code Map<String, Object>} genérico de {@code Catalog#getExtraFields()} sin conocer
 * sus nombres en tiempo de compilación, igual que {@code CatalogPersistenceConfig} ya
 * resuelve las propias clases de entidad por nombre en vez de un {@code Map.of(...)}
 * escrito a mano — añadir un campo a un tipo es config (+ una columna real en esa
 * entidad), no una clase Java nueva que tocar.
 */
final class CatalogExtraFieldsAccessor {

    private CatalogExtraFieldsAccessor() {
    }

    /** El valor actual de cada campo en {@code fieldNames}, leído de {@code entity} vía su getter. */
    static Map<String, Object> read(Object entity, List<String> fieldNames) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            PropertyDescriptor descriptor = descriptorFor(entity, fieldName);
            try {
                values.put(fieldName, descriptor.getReadMethod().invoke(entity));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(
                        "Cannot read extra field '" + fieldName + "' from " + entity.getClass().getSimpleName(), e);
            }
        }
        return values;
    }

    /**
     * Escribe cada campo en {@code fieldNames} sobre {@code entity} vía su setter, tomando el
     * valor de {@code values} ({@code null} si esa clave no está presente — igual que
     * {@code weight}/{@code translations}, esto sobrescribe el campo por completo, no lo fusiona).
     */
    static void write(Object entity, List<String> fieldNames, Map<String, Object> values) {
        for (String fieldName : fieldNames) {
            PropertyDescriptor descriptor = descriptorFor(entity, fieldName);
            try {
                descriptor.getWriteMethod().invoke(entity, values.get(fieldName));
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Cannot write extra field '" + fieldName + "' onto " + entity.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * El tipo de dato de cada campo en {@code fieldNames} (en ese orden), deducido del tipo Java
     * de la propiedad en {@code entityClass} y expresado como tipo JSON-friendly:
     * {@code string}, {@code integer}, {@code number}, {@code boolean}, {@code date},
     * {@code date-time} — o {@code string} para cualquier otro (enums, UUID, ...).
     */
    static Map<String, String> dataTypes(Class<?> entityClass, List<String> fieldNames) {
        Map<String, String> types = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            types.put(fieldName, dataTypeOf(descriptorFor(entityClass, fieldName).getPropertyType()));
        }
        return types;
    }

    private static String dataTypeOf(Class<?> javaType) {
        if (javaType == Integer.class || javaType == int.class || javaType == Long.class || javaType == long.class
                || javaType == Short.class || javaType == short.class || javaType == BigInteger.class) {
            return "integer";
        }
        if (Number.class.isAssignableFrom(javaType) || javaType == double.class || javaType == float.class) {
            return "number";
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        }
        if (javaType == LocalDate.class) {
            return "date";
        }
        if (Temporal.class.isAssignableFrom(javaType)) {
            return "date-time";
        }
        return "string";
    }

    private static PropertyDescriptor descriptorFor(Object entity, String fieldName) {
        return descriptorFor(entity.getClass(), fieldName);
    }

    private static PropertyDescriptor descriptorFor(Class<?> entityClass, String fieldName) {
        try {
            PropertyDescriptor descriptor = new PropertyDescriptor(fieldName, entityClass);
            if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                throw new IllegalStateException(
                        "Extra field '" + fieldName + "' has no getter/setter pair on " + entityClass.getSimpleName());
            }
            return descriptor;
        } catch (IntrospectionException e) {
            throw new IllegalStateException(
                    "Extra field '" + fieldName + "' is not a JavaBean property of " + entityClass.getSimpleName(), e);
        }
    }
}
