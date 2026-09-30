package com.ej1.app.common.rest.patch;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una única operación de un documento JSON Patch (<a href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902</a>).
 *
 * <p>{@code path} (y {@code from}, para {@code move}/{@code copy}) son JSON Pointers
 * tal como los define la <a href="https://www.rfc-editor.org/rfc/rfc6901">RFC 6901</a>.
 * {@code value} solo tiene sentido para {@code add}, {@code replace} y {@code test}.
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
