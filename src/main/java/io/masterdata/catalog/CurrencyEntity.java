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
 * Moneda (ISO 4217). {@code code} es su código de tres letras (EUR) y {@code numericCode} el
 * numérico (978); {@code decimals} son los decimales habituales (EUR 2, JPY 0, KWD 3) y
 * {@code symbol} su símbolo en inglés, o el propio código si no tiene uno distinto (CHF).
 */
@Entity
@Table(name = "currencies")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyEntity extends CatalogBaseEntity {

    @Column(name = "code", length = 3, unique = true)
    private String code;

    @Column(name = "numeric_code")
    private Integer numericCode;

    @Column(name = "decimals")
    private Integer decimals;

    @Column(name = "symbol", length = 10)
    private String symbol;
}
