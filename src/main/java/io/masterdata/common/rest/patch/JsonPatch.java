package io.masterdata.common.rest.patch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica un documento JSON Patch (<a href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902</a>)
 * a un documento JSON genérico representado como valores anidados de {@link Map}/{@link List}/escalares —
 * la misma forma que produce Jackson al deserializar a {@code Object}/{@code Map<String,Object>}.
 *
 * <p>Deliberadamente agnóstico del framework y de la versión de Jackson: nunca toca un
 * {@code JsonNode}. Eso importa aquí porque la capa web de Spring Boot 4.1 corre sobre
 * Jackson 3 ({@code tools.jackson}), mientras que toda librería de JSON Patch publicada
 * (java-json-tools, zjsonpatch, fge...) está construida contra el {@code JsonNode} de
 * Jackson 2 ({@code com.fasterxml.jackson.databind.JsonNode}). En lugar de traer una
 * segunda pila de Jackson incompatible solo para esto, las seis operaciones de la RFC 6902
 * se implementan directamente sobre colecciones Java planas.
 *
 * <p>Soporta las seis operaciones ({@code add}, {@code remove}, {@code replace}, {@code move},
 * {@code copy}, {@code test}) y la resolución de JSON Pointer de la RFC 6901, incluyendo el
 * token de anexado a array {@code "-"} y el escapado {@code ~0}/{@code ~1}. La aplicación es
 * atómica por llamada: el documento de entrada se copia en profundidad primero, de forma que
 * una operación que falla nunca deja visible al llamante un resultado parcialmente parcheado.
 */
public final class JsonPatch {

    private JsonPatch() {
    }

    /**
     * Topes frente a un patch hostil: {@code copy} duplica en profundidad, así que N operaciones
     * encadenadas pueden multiplicar el documento por 2^N. Con el tope de nodos comprobado tras cada
     * operación, el documento nunca pasa de ~2 × {@link #MAX_NODES}.
     */
    public static final int MAX_OPERATIONS = 50;
    public static final int MAX_NODES = 10_000;

    public static Object apply(Object document, List<JsonPatchOperation> operations) {
        if (operations.size() > MAX_OPERATIONS) {
            throw new JsonPatchException("too many operations: at most " + MAX_OPERATIONS + " allowed",
                    "error.jsonPatch.tooManyOperations", MAX_OPERATIONS);
        }
        Object result = deepCopy(document);
        for (int i = 0; i < operations.size(); i++) {
            JsonPatchOperation op = operations.get(i);
            result = applyOperation(result, op, i);
            if (countNodes(result) > MAX_NODES) {
                throw new JsonPatchException("resulting document is too large: at most " + MAX_NODES + " values allowed",
                        "error.jsonPatch.documentTooLarge", MAX_NODES);
            }
        }
        return result;
    }

    private static int countNodes(Object value) {
        int count = 1;
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                count += countNodes(child);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                count += countNodes(child);
            }
        }
        return count;
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
                default -> throw new JsonPatchException("Unsupported op '" + type + "'",
                        "error.jsonPatch.unsupportedOp", type);
            };
        } catch (JsonPatchTestFailedException e) {
            // debe capturarse antes que el JsonPatchException más general de abajo (es un
            // subtipo), para que una operación "test" fallida siga mapeando a 409 y no al
            // 400 genérico de los patches malformados.
            throw new JsonPatchTestFailedException(
                    "Operation " + index + " (\"" + type + "\" \"" + op.path() + "\"): " + e.getMessage(),
                    e, "error.jsonPatch.operationFailed", index, type, op.path());
        } catch (JsonPatchException e) {
            throw new JsonPatchException(
                    "Operation " + index + " (\"" + type + "\" \"" + op.path() + "\"): " + e.getMessage(),
                    e, "error.jsonPatch.operationFailed", index, type, op.path());
        }
    }

    // ---- operaciones ---------------------------------------------------

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
            throw new JsonPatchException("cannot add into a scalar value", "error.jsonPatch.addIntoScalar");
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Object remove(Object doc, String pointer) {
        List<String> tokens = tokenize(pointer);
        if (tokens.isEmpty()) {
            throw new JsonPatchException("cannot remove the whole document", "error.jsonPatch.removeWholeDocument");
        }
        String last = tokens.get(tokens.size() - 1);
        Object parent = get(doc, tokens.subList(0, tokens.size() - 1));
        if (parent instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (!map.containsKey(last)) {
                throw new JsonPatchException("member '" + last + "' does not exist",
                        "error.jsonPatch.memberNotFound", last);
            }
            map.remove(last);
        } else if (parent instanceof List<?> raw) {
            List<Object> list = (List<Object>) raw;
            list.remove(parseIndex(last, list.size(), false));
        } else {
            throw new JsonPatchException("cannot remove from a scalar value", "error.jsonPatch.removeFromScalar");
        }
        return doc;
    }

    private static Object replace(Object doc, String pointer, Object value) {
        List<String> tokens = tokenize(pointer);
        if (tokens.isEmpty()) {
            return deepCopy(value);
        }
        get(doc, tokens); // RFC 6902 §4.3: la ubicación destino DEBE existir
        remove(doc, pointer);
        return add(doc, pointer, value);
    }

    private static Object move(Object doc, String from, String path) {
        if (path.equals(from) || path.startsWith(from + "/")) {
            throw new JsonPatchException("cannot move a value into one of its own children",
                    "error.jsonPatch.moveIntoOwnChild");
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
                    "test failed: expected " + expected + " but was " + actual,
                    "error.jsonPatch.testFailed", expected, actual);
        }
        return doc;
    }

    // ---- JSON Pointer RFC 6901 -----------------------------------------

    private static List<String> tokenize(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        if (!pointer.startsWith("/")) {
            throw new JsonPatchException("invalid JSON Pointer '" + pointer + "' (must start with '/')",
                    "error.jsonPatch.invalidPointer", pointer);
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
                throw new JsonPatchException("member '" + token + "' does not exist",
                        "error.jsonPatch.memberNotFound", token);
            }
            return map.get(token);
        }
        if (current instanceof List<?> list) {
            return list.get(parseIndex(token, list.size(), false));
        }
        throw new JsonPatchException("cannot navigate into a scalar value at '" + token + "'",
                "error.jsonPatch.navigateIntoScalar", token);
    }

    private static int parseIndex(String token, int size, boolean forInsertion) {
        if (forInsertion && "-".equals(token)) {
            return size;
        }
        if (!token.matches("0|[1-9]\\d*")) {
            throw new JsonPatchException("invalid array index '" + token + "'",
                    "error.jsonPatch.invalidArrayIndex", token);
        }
        int index = Integer.parseInt(token);
        int max = forInsertion ? size : size - 1;
        if (index < 0 || index > max) {
            throw new JsonPatchException("array index " + index + " out of bounds (size " + size + ")",
                    "error.jsonPatch.arrayIndexOutOfBounds", index, size);
        }
        return index;
    }

    private static String requirePath(JsonPatchOperation op) {
        if (op.path() == null) {
            throw new JsonPatchException("missing 'path'", "error.jsonPatch.missingPath");
        }
        return op.path();
    }

    private static String requireFrom(JsonPatchOperation op) {
        if (op.from() == null) {
            throw new JsonPatchException("missing 'from'", "error.jsonPatch.missingFrom");
        }
        return op.from();
    }

    // ---- helpers de valores ---------------------------------------------------

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

    private static boolean isFinite(Number n) {
        return !(n instanceof Double d && !Double.isFinite(d)) && !(n instanceof Float f && !Float.isFinite(f));
    }

    private static boolean deepEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            // NaN/Infinity no son BigDecimal: new BigDecimal("Infinity") lanzaría NumberFormatException (500).
            return isFinite(na) && isFinite(nb)
                    ? new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString())) == 0
                    : na.equals(nb);
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
