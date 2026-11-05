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
 * Idioma. {@code alpha2} es su código ISO 639-1 (es) y {@code alpha3} el ISO 639-2/T (spa), únicos.
 */
@Entity
@Table(name = "languages")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LanguageEntity extends CatalogBaseEntity {

    @Column(name = "alpha2", length = 2, unique = true)
    private String alpha2;

    @Column(name = "alpha3", length = 3, unique = true)
    private String alpha3;
}
