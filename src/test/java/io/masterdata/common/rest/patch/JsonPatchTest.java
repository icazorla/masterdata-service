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

    @Test
    @DisplayName("op ausente falla como op no soportada")
    void missingOpFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op(null, "/foo", "x"))))
                .isInstanceOf(JsonPatchException.class)
                .cause().hasMessageContaining("Unsupported op ''");
    }

    @Test
    @DisplayName("path ausente falla, y from ausente en move/copy también")
    void missingPathOrFromFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("add", null, "x"))))
                .cause().hasMessage("missing 'path'");
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(moveOrCopy("copy", null, "/baz"))))
                .cause().hasMessage("missing 'from'");
    }

    @Test
    @DisplayName("un JSON Pointer que no empieza por '/' es inválido")
    void pointerWithoutLeadingSlashFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("add", "foo", "x"))))
                .cause().hasMessageContaining("must start with '/'");
    }

    // ---- documento completo (pointer "") ------------------------------

    @Test
    @DisplayName("add y replace sobre el pointer vacío sustituyen el documento entero")
    void addAndReplaceWholeDocument() {
        assertThat(JsonPatch.apply(map("foo", "bar"), List.of(op("add", "", map("baz", 1)))))
                .isEqualTo(map("baz", 1));
        assertThat(JsonPatch.apply(map("foo", "bar"), List.of(op("replace", "", List.of("x")))))
                .isEqualTo(List.of("x"));
    }

    @Test
    @DisplayName("remove sobre el pointer vacío falla: no se puede borrar el documento entero")
    void removeWholeDocumentFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("remove", "", null))))
                .cause().hasMessage("cannot remove the whole document");
    }

    // ---- valores escalares e índices ------------------------------------

    @Test
    @DisplayName("add y remove dentro de un escalar fallan")
    void addOrRemoveInsideScalarFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("add", "/foo/x", 1))))
                .cause().hasMessage("cannot add into a scalar value");
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("remove", "/foo/x", null))))
                .cause().hasMessage("cannot remove from a scalar value");
    }

    @Test
    @DisplayName("navegar a través de un escalar falla")
    void navigateThroughScalarFails() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), List.of(op("add", "/foo/x/y", 1))))
                .cause().hasMessage("cannot navigate into a scalar value at 'x'");
    }

    @Test
    @DisplayName("un índice de array que no es un número falla")
    void nonNumericArrayIndexFails() {
        Map<String, Object> doc = map("items", List.of("a"));

        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("remove", "/items/abc", null))))
                .cause().hasMessage("invalid array index 'abc'");
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("remove", "/items/01", null))))
                .cause().hasMessage("invalid array index '01'");
    }

    @Test
    @DisplayName("test navega por los elementos de un array")
    void test_navigatesIntoArray() {
        Map<String, Object> doc = map("items", List.of(map("name", "a"), map("name", "b")));

        Object result = JsonPatch.apply(doc, List.of(op("test", "/items/1/name", "b")));

        assertThat(result).isEqualTo(doc);
    }

    // ---- igualdad profunda de test --------------------------------------

    @Test
    @DisplayName("test compara null: solo es igual a null")
    void test_nullEquality() {
        Map<String, Object> doc = map("foo", null);

        assertThat(JsonPatch.apply(doc, List.of(op("test", "/foo", null)))).isEqualTo(doc);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/foo", "x"))))
                .isInstanceOf(JsonPatchTestFailedException.class);
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "x"), List.of(op("test", "/foo", null))))
                .isInstanceOf(JsonPatchTestFailedException.class);
    }

    @Test
    @DisplayName("test compara objetos miembro a miembro, sin importar el orden")
    void test_objectEquality() {
        Map<String, Object> doc = map("obj", map("a", 1, "b", List.of(1, 2)));

        assertThat(JsonPatch.apply(doc, List.of(op("test", "/obj", map("b", List.of(1.0, 2), "a", 1)))))
                .isEqualTo(doc);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/obj", map("a", 1)))))
                .as("distinto número de miembros").isInstanceOf(JsonPatchTestFailedException.class);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/obj", map("a", 1, "c", List.of(1, 2))))))
                .as("miembro que falta").isInstanceOf(JsonPatchTestFailedException.class);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/obj", map("a", 2, "b", List.of(1, 2))))))
                .as("miembro con otro valor").isInstanceOf(JsonPatchTestFailedException.class);
    }

    @Test
    @DisplayName("test compara arrays elemento a elemento y en orden")
    void test_arrayEquality() {
        Map<String, Object> doc = map("items", List.of("a", "b"));

        assertThat(JsonPatch.apply(doc, List.of(op("test", "/items", List.of("a", "b"))))).isEqualTo(doc);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/items", List.of("a")))))
                .as("distinta longitud").isInstanceOf(JsonPatchTestFailedException.class);
        assertThatThrownBy(() -> JsonPatch.apply(doc, List.of(op("test", "/items", List.of("b", "a")))))
                .as("otro orden").isInstanceOf(JsonPatchTestFailedException.class);
    }

    @Test
    @DisplayName("test no considera iguales valores de distinto tipo JSON")
    void test_differentTypesAreNotEqual() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "1"), List.of(op("test", "/foo", 1))))
                .isInstanceOf(JsonPatchTestFailedException.class);
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", List.of()), List.of(op("test", "/foo", map()))))
                .isInstanceOf(JsonPatchTestFailedException.class);
    }

    @Test
    @DisplayName("test con NaN/Infinity no revienta con NumberFormatException: simplemente no coincide, o coincide si son iguales")
    void test_nonFiniteNumbers() {
        assertThatThrownBy(() -> JsonPatch.apply(map("foo", 1), List.of(op("test", "/foo", Double.POSITIVE_INFINITY))))
                .isInstanceOf(JsonPatchTestFailedException.class);
        assertThat(JsonPatch.apply(map("foo", Double.NaN), List.of(op("test", "/foo", Double.NaN))))
                .isEqualTo(map("foo", Double.NaN));
    }

    // ---- topes frente a un patch hostil ------------------------------------

    @Test
    @DisplayName("Más de MAX_OPERATIONS operaciones se rechazan antes de aplicar ninguna")
    void tooManyOperationsAreRejected() {
        List<JsonPatchOperation> ops = java.util.Collections.nCopies(JsonPatch.MAX_OPERATIONS + 1, op("test", "/foo", "bar"));

        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), ops))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("too many operations");
    }

    @Test
    @DisplayName("copy encadenado que duplica el documento (2^N) se corta al superar MAX_NODES")
    void doublingCopiesAreCutOff() {
        // Copiar el documento entero dentro de sí mismo, con una clave nueva cada vez, lo duplica en cada operación.
        List<JsonPatchOperation> ops = new java.util.ArrayList<>();
        for (int i = 0; i < JsonPatch.MAX_OPERATIONS; i++) {
            ops.add(moveOrCopy("copy", "", "/c" + i));
        }

        assertThatThrownBy(() -> JsonPatch.apply(map("foo", "bar"), ops))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("too large");
    }
}
