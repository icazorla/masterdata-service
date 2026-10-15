package io.masterdata.common.catalog.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code @AllArgsConstructor(access = PACKAGE)} en vez de público (el valor por defecto):
 * con {@code -parameters} habilitado en la compilación, Jackson detectaba un constructor
 * todo-argumentos público como creador implícito basado en propiedades y lo prefería sobre
 * el constructor sin argumentos + setters, saltándose tanto los inicializadores de campo
 * como {@code @Builder.Default} para cualquier propiedad ausente del JSON (p. ej.
 * {@code extraFields} llegaba {@code null} en vez de un mapa vacío, reventando el
 * {@code @JsonAnySetter} de abajo). {@code @Builder} sigue necesitando que este
 * constructor exista (con {@code @NoArgsConstructor} también presente, {@code @Builder}
 * ya no genera el suyo propio), pero de paquete le basta — solo lo llama {@code build()},
 * en esta misma clase — y Jackson no lo detecta como creador implícito al no ser público.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
@ValidCatalogTranslations
@Schema(description = "Payload de creación/actualización de una entrada de catálogo (comunidad autónoma, provincia, ...)")
public class CatalogRequest {

    @Builder.Default
    @Schema(description = "Peso para ordenación manual (sortBy=weight), no traducido — a diferencia de name/description no depende del idioma", example = "0")
    private Integer weight = 0;

    @Builder.Default
    @Schema(description = "Si la entrada está activa. Las desactivadas no aparecen en los listados de la API de consulta, pero siguen resolviéndose por id", example = "true")
    private Boolean enabled = true;

    @NotEmpty
    @Schema(description = "Traducciones de la entrada; clave = locale (\"en\", \"es\"...), debe incluir al menos el idioma por defecto (en)",
            example = "{\"en\":{\"name\":\"Logitech\",\"description\":\"Peripheral manufacturer\"},"
                    + "\"es\":{\"name\":\"Logitech\",\"description\":\"Fabricante de periféricos\"}}")
    private Map<String, @Valid CatalogTranslationRequest> translations;

    /**
     * Campos adicionales propios de {@code type} (p. ej. {@code regionCode} solo para
     * {@code province}: las columnas propias de su entidad), viajando en el JSON como atributos normales de primer
     * nivel (no anidados bajo una clave {@code "extraFields"}) gracias a
     * {@link JsonAnyGetter}/{@link JsonAnySetter}. Se sobrescriben por completo en cada
     * escritura, igual que {@code translations} — omitir uno existente lo borra.
     */
    @Builder.Default
    @Setter(AccessLevel.NONE)
    @Schema(hidden = true)
    private Map<String, Object> extraFields = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    /**
     * Atributos de {@link CatalogAdminResponse} que asigna el servidor: se ignoran para que un formulario
     * pueda reenviar tal cual lo que recibió. Cualquier otro atributo desconocido se rechaza al guardar.
     */
    private static final Set<String> READ_ONLY_FIELDS = Set.of("id", "type", "createdAt", "updatedAt", "version");

    @JsonAnySetter
    public void setExtraField(String name, Object value) {
        if (!READ_ONLY_FIELDS.contains(name)) {
            extraFields.put(name, value);
        }
    }
}
