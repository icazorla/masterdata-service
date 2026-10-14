package io.masterdata.common.catalog.infrastructure.persistence;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.support.DefaultConversionService;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    private static final DefaultConversionService CONVERSION_SERVICE = new DefaultConversionService();

    private CatalogExtraFieldsAccessor() {
    }

    /**
     * {@code raw} (un valor de filtro de la query string) convertido al tipo Java real de la
     * propiedad {@code fieldName} de {@code entityClass} — p. ej. {@code Integer} y no
     * {@code Long} — para que Hibernate lo acepte como parámetro de una comparación con esa
     * columna. Las fechas ({@code java.time}) se leen en ISO-8601 con su propio
     * {@code parse}; el resto lo convierte el {@link DefaultConversionService} de Spring.
     *
     * @throws IllegalArgumentException si {@code raw} no es un valor válido para ese tipo
     */
    static Object convert(Class<?> entityClass, String fieldName, String raw) {
        Class<?> javaType = descriptorFor(entityClass, fieldName).getPropertyType();
        try {
            if (Temporal.class.isAssignableFrom(javaType)) {
                Method parse = javaType.getMethod("parse", CharSequence.class);
                return parse.invoke(null, raw);
            }
            return CONVERSION_SERVICE.convert(raw, javaType);
        } catch (InvocationTargetException | ConversionException e) {
            throw new IllegalArgumentException("Cannot convert '" + raw + "' to " + javaType.getSimpleName(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No ISO parse(CharSequence) method on " + javaType.getName(), e);
        }
    }

    /** El tipo de dato ({@code string}, {@code integer}, ... — ver {@link #dataTypes}) de un único campo. */
    static String dataType(Class<?> entityClass, String fieldName) {
        return dataTypeOf(descriptorFor(entityClass, fieldName).getPropertyType());
    }

    /**
     * Si la propiedad es texto libre ({@link String}) — admite {@code like}. Otros tipos que
     * la API también expone como {@code string} (enums, UUID, ...) no: se comparan por igualdad.
     */
    static boolean isText(Class<?> entityClass, String fieldName) {
        return descriptorFor(entityClass, fieldName).getPropertyType() == String.class;
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
