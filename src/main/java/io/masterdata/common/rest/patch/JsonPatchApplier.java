package io.masterdata.common.rest.patch;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplica un documento JSON Patch (RFC 6902) a un objeto tipado de principio a fin: convierte
 * {@code current} a la forma genérica Map/List sobre la que opera {@link JsonPatch}, delega
 * la aplicación real del patch en {@link JsonPatch}, convierte el resultado de vuelta al tipo
 * de {@code current}, y lo revalida con Bean Validation.
 *
 * <p>Se mantiene separado de {@link JsonPatch} a propósito: {@code JsonPatch} sigue siendo una
 * implementación pura y agnóstica del framework de la RFC 6902 sobre colecciones Java planas
 * (ver su Javadoc), mientras que esta clase es la orquestación consciente de Jackson/Bean
 * Validation que usan los controladores REST.
 */
@Component
@RequiredArgsConstructor
public class JsonPatchApplier {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Aplica {@code patch} a {@code current} y devuelve el objeto resultante, ya validado.
     *
     * @throws JsonPatchException           si el documento de patch está malformado o no se puede aplicar
     * @throws JsonPatchTestFailedException si el valor de una operación {@code "test"} no coincide
     * @throws ConstraintViolationException si el resultado parcheado viola alguna restricción
     *                                       de Bean Validation
     */
    @SuppressWarnings("unchecked")
    public <T> T apply(T current, List<JsonPatchOperation> patch) {
        Map<String, Object> document =
                (Map<String, Object>) (Map<?, ?>) objectMapper.convertValue(current, Map.class);

        Object patchedDoc = JsonPatch.apply(document, patch);

        Class<T> type = (Class<T>) current.getClass();
        T result = objectMapper.convertValue(patchedDoc, type);

        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return result;
    }
}
