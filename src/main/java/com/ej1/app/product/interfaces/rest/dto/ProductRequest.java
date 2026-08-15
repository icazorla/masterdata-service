package com.ej1.app.product.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload de creación/actualización de un producto")
public class ProductRequest {

    @NotBlank
    @Size(max = 120)
    @Schema(description = "Nombre del producto", example = "Teclado mecánico", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 2000)
    @Schema(description = "Descripción larga del producto", example = "Switches brown")
    private String description;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Precio unitario", example = "89.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @NotNull
    @Min(0)
    @Schema(description = "Unidades disponibles en stock", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stock;

    @NotBlank
    @Size(max = 40)
    @Schema(description = "SKU único del producto", example = "KB-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sku;

    @Size(max = 80)
    @Schema(description = "Categoría del producto", example = "Periféricos")
    private String category;

    @Size(max = 80)
    @Schema(description = "Marca del producto", example = "Logitech")
    private String brand;

    @Size(max = 500)
    @Schema(description = "URL de la imagen del producto")
    private String imageUrl;

    @Positive
    @Digits(integer = 8, fraction = 3)
    @Schema(description = "Peso en kg", example = "1.200")
    private BigDecimal weight;

    @Size(max = 50)
    @Schema(description = "Dimensiones físicas", example = "45x15x3 cm")
    private String dimensions;

    @Size(max = 500)
    @Schema(description = "Tags separados por coma", example = "teclado,rgb")
    private String tags;

    @Schema(description = "Si el producto está activo. Por defecto true", example = "true")
    private Boolean active;
}
