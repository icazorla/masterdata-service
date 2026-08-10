package com.ej1.app.product.interfaces.rest.dto;

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
public class ProductRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stock;

    @NotBlank
    @Size(max = 40)
    private String sku;

    @Size(max = 80)
    private String category;

    @Size(max = 80)
    private String brand;

    @Size(max = 500)
    private String imageUrl;

    @Positive
    @Digits(integer = 8, fraction = 3)
    private BigDecimal weight;

    @Size(max = 50)
    private String dimensions;

    @Size(max = 500)
    private String tags;

    private Boolean active;
}
