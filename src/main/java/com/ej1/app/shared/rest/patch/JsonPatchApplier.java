package com.ej1.app.shared.rest.patch;

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
 * Applies a JSON Patch (RFC 6902) document to a typed object end-to-end: converts {@code current}
 * to the generic Map/List shape {@link JsonPatch} operates on, delegates the actual patch
 * application to {@link JsonPatch}, converts the result back to {@code current}'s type, and
 * re-validates it with Bean Validation.
 *
 * <p>Kept separate from {@link JsonPatch} on purpose: {@code JsonPatch} stays a pure,
 * framework-agnostic implementation of RFC 6902 over plain Java collections (see its Javadoc),
 * while this class is the Jackson/Bean-Validation-aware orchestration used by REST controllers.
 */
@Component
@RequiredArgsConstructor
public class JsonPatchApplier {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Applies {@code patch} to {@code current} and returns the resulting, already-validated
     * object.
     *
     * @throws JsonPatchException           if the patch document is malformed or not applicable
     * @throws JsonPatchTestFailedException if a {@code "test"} operation's value does not match
     * @throws ConstraintViolationException if the patched result violates a Bean Validation
     *                                       constraint
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
