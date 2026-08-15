package com.ej1.app.shared.rest.patch;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single operation of a JSON Patch document (<a href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902</a>).
 *
 * <p>{@code path} (and {@code from}, for {@code move}/{@code copy}) are JSON Pointers
 * as defined by <a href="https://www.rfc-editor.org/rfc/rfc6901">RFC 6901</a>.
 * {@code value} is only meaningful for {@code add}, {@code replace} and {@code test}.
 */
@Schema(description = "Operación JSON Patch (RFC 6902)")
public record JsonPatchOperation(

        @Schema(description = "Tipo de operación", example = "replace",
                allowableValues = {"add", "remove", "replace", "move", "copy", "test"})
        String op,

        @Schema(description = "JSON Pointer (RFC 6901) del elemento objetivo", example = "/price")
        String path,

        @Schema(description = "Valor a aplicar. Requerido para add/replace/test, ignorado en remove/move/copy.")
        Object value,

        @Schema(description = "JSON Pointer de origen. Requerido solo para move/copy.", example = "/tags")
        String from
) {
}
