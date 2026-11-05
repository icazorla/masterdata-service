package io.masterdata.catalog;

import io.masterdata.common.catalog.infrastructure.persistence.CatalogBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tipo de IVA (u otro impuesto). {@code rate} es el porcentaje (21 = 21 %) y {@code validFrom} /
 * {@code validTo} el periodo en que estuvo en vigor, ambos inclusivos; {@code null} en
 * {@code validTo} significa que sigue vigente, y en {@code validFrom} que no consta desde cuándo.
 * Un tipo que ya no se aplica se desactiva ({@code enabled=false}) en vez de borrarse, para
 * poder seguir resolviendo los datos antiguos que lo referencian.
 */
@Entity
@Table(name = "tax_rates")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRateEntity extends CatalogBaseEntity {

    @Column(name = "rate", precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
