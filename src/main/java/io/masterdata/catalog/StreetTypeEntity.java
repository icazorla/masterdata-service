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

/**
 * Tipo de vía de una dirección (calle, avenida, plaza...). {@code abbreviation} es su abreviatura
 * habitual al escribir la dirección ({@code C/}, {@code Avda.}, {@code Pza.}).
 */
@Entity
@Table(name = "street_types")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreetTypeEntity extends CatalogBaseEntity {

    @Column(name = "abbreviation", length = 10)
    private String abbreviation;
}
