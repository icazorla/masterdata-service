package io.masterdata.common.rest.patch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica {@link JsonPatch} contra la semántica de operaciones de la RFC 6902,
 * incluyendo varios de los ejemplos de la propia RFC (§4, apéndice A).
 */
class JsonPatchTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static JsonPatchOperation op(String type, String path, Object value) {
        return new JsonPatchOperation(type, path, value, null);
    }

    private static JsonPatchOperation moveOrCopy(String type, String from, String path) {
        return new JsonPatchOperation(type, path, null, from);
    }

    // ---- add ---------------------------------------------------------

    @Test
    @DisplayName("add: crea un nuevo miembro en un objeto")
    void add_objectMember() {
        Map<String, Object> doc = map("foo", "bar");

        Object result = JsonPatch.apply(doc, List.of(op("add", "/baz", "qux")));

        assertThat(result).isEqualTo(map("foo", "bar", "baz", "qux"));
    }

    @Test
    @DisplayName("add: reemplaza un miembro existente")
    void add_replacesExistingMember() {
        Map<String, Object> doc = map("foo", "bar");

        Object result = JsonPatch.apply(doc, List.of(op("add", "/foo", "new")));

        assertThat(result).isEqualTo(map("foo", "new"));
    }

    @Test
    @DisplayName("add: inserta en un array por índice")
    void add_arrayByIndex() {
        Map<String, Object> doc = map("items", List.of("a", "c"));

        Object result = JsonPatch.apply(doc, List.of(op("add", "/items/1", "b")));

        assertThat(result).isEqualTo(map("items", List.of("a", "b", "c")));
    }

    @Test
    @DisplayName("add: '-' añade al final del array")
    void add_arrayAppendDash() {
        Map<String, Object> doc = map("items", List.of("a", "b"));

        Object result = JsonPatch.apply(doc, List.of(op("add", "/items/-", "c")));

        assertThat(result).isEqualTo(map("items", List.of("a", "b", "c")));
    }

    @Test
    @DisplayName("add: índice fuera de rango falla")
    void add_arrayOutOfBounds() {
        Map<String, Object> doc = map("items", List.of("a"));

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("add", "/items/5", "x"))))
                .isInstanceOf(JsonPatchException.class);
    }

    // ---- remove --------------------------------------------------------

    @Test
    @DisplayName("remove: elimina un miembro de un objeto")
    void remove_objectMember() {
        Map<String, Object> doc = map("foo", "bar", "baz", "qux");

        Object result = JsonPatch.apply(doc, List.of(op("remove", "/baz", null)));

        assertThat(result).isEqualTo(map("foo", "bar"));
    }

    @Test
    @DisplayName("remove: elimina un elemento de un array")
    void remove_arrayElement() {
        Map<String, Object> doc = map("items", List.of("a", "b", "c"));

        Object result = JsonPatch.apply(doc, List.of(op("remove", "/items/1", null)));

        assertThat(result).isEqualTo(map("items", List.of("a", "c")));
    }

    @Test
    @DisplayName("remove: falla si el path no existe")
    void remove_missingPath() {
        Map<String, Object> doc = map("foo", "bar");

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("remove", "/missing", null))))
                .isInstanceOf(JsonPatchException.class);
    }

    // ---- replace ---------------------------------------------------------

    @Test
    @DisplayName("replace: sustituye un valor existente")
    void replace_existingValue() {
        Map<String, Object> doc = map("foo", "bar");

        Object result = JsonPatch.apply(doc, List.of(op("replace", "/foo", "new")));

        assertThat(result).isEqualTo(map("foo", "new"));
    }

    @Test
    @DisplayName("replace: falla si el path no existe")
    void replace_missingPathFails() {
        Map<String, Object> doc = map("foo", "bar");

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("replace", "/missing", "x"))))
                .isInstanceOf(JsonPatchException.class);
    }

    // ---- move ---------------------------------------------------------

    @Test
    @DisplayName("move: mueve un valor de un lugar a otro")
    void move_value() {
        Map<String, Object> doc = map("foo", map("bar", "baz", "waldo", "fred"), "qux", map("corge", "grault"));

        Object result = JsonPatch.apply(doc, List.of(moveOrCopy("move", "/foo/waldo", "/qux/thud")));

        assertThat(result).isEqualTo(map(
                "foo", map("bar", "baz"),
                "qux", map("corge", "grault", "thud", "fred")));
    }

    @Test
    @DisplayName("move: no permite mover un valor dentro de uno de sus propios hijos")
    void move_intoOwnChildFails() {
        Map<String, Object> doc = map("a", map("b", "c"));

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(moveOrCopy("move", "/a", "/a/b"))))
                .isInstanceOf(JsonPatchException.class);
    }

    // ---- copy ---------------------------------------------------------

    @Test
    @DisplayName("copy: copia un valor sin eliminar el origen")
    void copy_value() {
        Map<String, Object> doc = map("foo", map("bar", "baz", "waldo", "fred"), "qux", map("corge", "grault"));

        Object result = JsonPatch.apply(doc, List.of(moveOrCopy("copy", "/foo/waldo", "/qux/thud")));

        assertThat(result).isEqualTo(map(
                "foo", map("bar", "baz", "waldo", "fred"),
                "qux", map("corge", "grault", "thud", "fred")));
    }

    // ---- test ---------------------------------------------------------

    @Test
    @DisplayName("test: no falla cuando el valor coincide")
    void test_matchingValuePasses() {
        Map<String, Object> doc = map("baz", "qux", "foo", List.of("a", 2, "c"));

        Object result = JsonPatch.apply(doc, List.of(op("test", "/baz", "qux")));

        assertThat(result).isEqualTo(doc);
    }

    @Test
    @DisplayName("test: falla con JsonPatchTestFailedException cuando el valor no coincide")
    void test_mismatchThrowsTestFailedException() {
        Map<String, Object> doc = map("baz", "qux");

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/baz", "different"))))
                .isInstanceOf(JsonPatchTestFailedException.class);
    }

    @Test
    @DisplayName("test: compara números por valor, no por tipo (10 == 10.0)")
    void test_numericEqualityIgnoresType() {
        Map<String, Object> doc = map("price", 10);

        Object result = JsonPatch.apply(doc, List.of(op("test", "/price", 10.0)));

        assertThat(result).isEqualTo(doc);
    }

    // ---- composición / atomicidad ---------------------------------------------------

    @Test
    @DisplayName("aplica varias operaciones en secuencia")
    void appliesMultipleOperationsInSequence() {
        Map<String, Object> doc = map("name", "Teclado", "price", 89.99, "active", true);

        Object result = JsonPatch.apply(doc, List.of(
                op("replace", "/price", 79.99),
                op("replace", "/active", false),
                op("add", "/description", "Oferta")));

        assertThat(result).isEqualTo(map(
                "name", "Teclado", "price", 79.99, "active", false, "description", "Oferta"));
    }

    @Test
    @DisplayName("no muta el documento original")
    void doesNotMutateOriginalDocument() {
        Map<String, Object> doc = map("foo", "bar");

        JsonPatch.apply(doc, List.of(op("replace", "/foo", "changed")));

        assertThat(doc).isEqualTo(map("foo", "bar"));
    }

    @Test
    @DisplayName("op desconocida falla")
    void unknownOpFails() {
        Map<String, Object> doc = map("foo", "bar");

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("bogus", "/foo", "x"))))
                .isInstanceOf(JsonPatchException.class);
    }
}
