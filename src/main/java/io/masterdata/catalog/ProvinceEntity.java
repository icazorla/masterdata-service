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
 * Provincia de España (incluye Ceuta y Melilla). {@code ineCode} es su código oficial del
 * INE (1 = Araba/Álava ... 52 = Melilla) y {@code regionCode} el {@code ineCode} de su
 * comunidad autónoma ({@link RegionEntity}).
 *
 * <p>{@code regionCode} es un código y no una relación JPA a propósito: los campos adicionales
 * de un catálogo solo pueden ser columnas simples (ver {@code CatalogPersistenceConfig}), y así
 * se filtra directamente, p. ej. {@code filter=regionCode:1} para las provincias de Andalucía.
 */
@Entity
@Table(name = "provinces")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvinceEntity extends CatalogBaseEntity {

    @Column(name = "ine_code", unique = true)
    private Integer ineCode;

    @Column(name = "region_code")
    private Integer regionCode;
}
