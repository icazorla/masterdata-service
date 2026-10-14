package io.masterdata.catalog;

import io.masterdata.common.catalog.infrastructure.persistence.CatalogBaseEntity;
import io.masterdata.common.catalog.infrastructure.persistence.CatalogEntity;
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
 * Comunidad autónoma de España (incluye las ciudades autónomas de Ceuta y Melilla).
 * {@code ineCode} es su código oficial del INE (1 = Andalucía ... 19 = Melilla), que es
 * también el que referencia {@link ProvinceEntity#getRegionCode()}.
 */
@Entity
@Table(name = "regions")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionEntity extends CatalogBaseEntity implements CatalogEntity {

    @Column(name = "ine_code", unique = true)
    private Integer ineCode;
}
