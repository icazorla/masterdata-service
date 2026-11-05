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
 * País (ISO 3166-1). {@code alpha2} (ES) y {@code alpha3} (ESP) son sus códigos oficiales, únicos.
 */
@Entity
@Table(name = "countries")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryEntity extends CatalogBaseEntity {

    @Column(name = "alpha2", length = 2, unique = true)
    private String alpha2;

    @Column(name = "alpha3", length = 3, unique = true)
    private String alpha3;
}
