package com.ej1.app.shared.rest.patch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a JSON Patch document (<a href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902</a>)
 * to a generic JSON document represented as nested {@link Map}/{@link List}/scalar values —
 * the same shape Jackson produces when deserializing into {@code Object}/{@code Map<String,Object>}.
 *
 * <p>Deliberately framework- and Jackson-version-agnostic: it never touches a {@code JsonNode}.
 * That matters here because Spring Boot 4.1's web layer runs on Jackson 3 ({@code tools.jackson}),
 * while every published JSON Patch library (java-json-tools, zjsonpatch, fge...) is built against
 * Jackson 2's {@code com.fasterxml.jackson.databind.JsonNode}. Rather than pull in a second,
 * incompatible Jackson stack just for this, the six RFC 6902 operations are implemented directly
 * over plain Java collections.
 *
 * <p>Supports all six operations ({@code add}, {@code remove}, {@code replace}, {@code move},
 * {@code copy}, {@code test}) and RFC 6901 JSON Pointer resolution, including the {@code "-"}
 * array-append token and {@code ~0}/{@code ~1} escaping. Application is atomic per call: the
 * input document is deep-copied first, so a failing operation never leaves a partially-patched
 * result visible to the caller.
 */
public final class JsonPatch {

    private JsonPatch() {
    }

    public static Object apply(Object document, List<JsonPatchOperation> operations) {
        Object result = deepCopy(document);
        for (int i = 0; i < operations.size(); i++) {
            JsonPatchOperation op = operations.get(i);
            result = applyOperation(result, op, i);
        }
        return result;
    }

    private static Object applyOperation(Object doc, JsonPatchOperation op, int index) {
        String type = op.op() == null ? "" : op.op();
        try {
            return switch (type) {
                case "add" -> add(doc, requirePath(op), op.value());
                case "remove" -> remove(doc, requirePath(op));
                case "replace" -> replace(doc, requirePath(op), op.value());
                case "move" -> move(doc, requireFrom(op), requirePath(op));
                case "copy" -> copy(doc, requireFrom(op), requirePath(op));
                case "test" -> test(doc, requirePath(op), op.value());
                default -> throw new JsonPatchException("Unsupported op '" + type + "'");
            };
        } catch (JsonPatchTestFailedException e) {
            // must be caught before the broader JsonPatchException below (it's a subtype) so a
            // failed "test" op still maps to 409, not the generic 400 for malformed patches.
            throw new JsonPatchTestFailedException("Operation " + index + " (\"" + type + "\" \"" + op.path() + "\"): " + e.getMessage());
        } catch (JsonPatchException e) {
            throw new JsonPatchException("Operation " + index + " (\"" + type + "\" \"" + op.path() + "\"): " + e.getMessage());
        }
    }

    // ---- operations ---------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object add(Object doc, String pointer, Object value) {
        List<String> tokens = tokenize(pointer);
        if (tokens.isEmpty()) {
            return deepCopy(value);
        }
        String last = tokens.get(tokens.size() - 1);
        Object parent = get(doc, tokens.subList(0, tokens.size() - 1));
        if (parent instanceof Map<?, ?> raw) {
            ((Map<String, Object>) raw).put(last, deepCopy(value));
        } else if (parent instanceof List<?> raw) {
            List<Object> list = (List<Object>) raw;
            list.add(parseIndex(last, list.size(), true), deepCopy(value));
        } else {
            throw new JsonPatchException("cannot add into a scalar value");
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Object remove(Object doc, String pointer) {
        List<String> tokens = tokenize(pointer);
        if (tokens.isEmpty()) {
            throw new JsonPatchException("cannot remove the whole document");
        }
        String last = tokens.get(tokens.size() - 1);
        Object parent = get(doc, tokens.subList(0, tokens.size() - 1));
        if (parent instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (!map.containsKey(last)) {
                throw new JsonPatchException("member '" + last + "' does not exist");
            }
            map.remove(last);
        } else if (parent instanceof List<?> raw) {
            List<Object> list = (List<Object>) raw;
            list.remove(parseIndex(last, list.size(), false));
        } else {
            throw new JsonPatchException("cannot remove from a scalar value");
        }
        return doc;
    }

    private static Object replace(Object doc, String pointer, Object value) {
        List<String> tokens = tokenize(pointer);
        if (tokens.isEmpty()) {
            return deepCopy(value);
        }
        get(doc, tokens); // RFC 6902 §4.3: target location MUST exist
        remove(doc, pointer);
        return add(doc, pointer, value);
    }

    private static Object move(Object doc, String from, String path) {
        if (path.equals(from) || path.startsWith(from + "/")) {
            throw new JsonPatchException("cannot move a value into one of its own children");
        }
        Object value = deepCopy(get(doc, tokenize(from)));
        Object afterRemove = remove(doc, from);
        return add(afterRemove, path, value);
    }

    private static Object copy(Object doc, String from, String path) {
        Object value = deepCopy(get(doc, tokenize(from)));
        return add(doc, path, value);
    }

    private static Object test(Object doc, String pointer, Object expected) {
        Object actual = get(doc, tokenize(pointer));
        if (!deepEquals(actual, expected)) {
            throw new JsonPatchTestFailedException(
                    "test failed: expected " + expected + " but was " + actual);
        }
        return doc;
    }

    // ---- RFC 6901 JSON Pointer -----------------------------------------

    private static List<String> tokenize(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        if (!pointer.startsWith("/")) {
            throw new JsonPatchException("invalid JSON Pointer '" + pointer + "' (must start with '/')");
        }
        String[] parts = pointer.substring(1).split("/", -1);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            tokens.add(part.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }

    private static Object get(Object doc, List<String> tokens) {
        Object current = doc;
        for (String token : tokens) {
            current = step(current, token);
        }
        return current;
    }

    private static Object step(Object current, String token) {
        if (current instanceof Map<?, ?> map) {
            if (!map.containsKey(token)) {
                throw new JsonPatchException("member '" + token + "' does not exist");
            }
            return map.get(token);
        }
        if (current instanceof List<?> list) {
            return list.get(parseIndex(token, list.size(), false));
        }
        throw new JsonPatchException("cannot navigate into a scalar value at '" + token + "'");
    }

    private static int parseIndex(String token, int size, boolean forInsertion) {
        if (forInsertion && "-".equals(token)) {
            return size;
        }
        if (!token.matches("0|[1-9]\\d*")) {
            throw new JsonPatchException("invalid array index '" + token + "'");
        }
        int index = Integer.parseInt(token);
        int max = forInsertion ? size : size - 1;
        if (index < 0 || index > max) {
            throw new JsonPatchException("array index " + index + " out of bounds (size " + size + ")");
        }
        return index;
    }

    private static String requirePath(JsonPatchOperation op) {
        if (op.path() == null) {
            throw new JsonPatchException("missing 'path'");
        }
        return op.path();
    }

    private static String requireFrom(JsonPatchOperation op) {
        if (op.from() == null) {
            throw new JsonPatchException("missing 'from'");
        }
        return op.from();
    }

    // ---- value helpers ---------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    private static boolean deepEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString())) == 0;
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (ma.size() != mb.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : ma.entrySet()) {
                if (!mb.containsKey(entry.getKey()) || !deepEquals(entry.getValue(), mb.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!deepEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }
}
