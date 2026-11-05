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
 * Tipo de documento de identidad (DNI, NIE, pasaporte...). {@code code} es su código corto, único, y
 * {@code pattern} una expresión regular con el formato que debe tener su número, para validar
 * formularios; solo comprueba el formato, no la letra o el dígito de control. {@code null} si el
 * documento no tiene un formato fijo.
 */
@Entity
@Table(name = "identity_document_types")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityDocumentTypeEntity extends CatalogBaseEntity {

    @Column(name = "code", length = 10, unique = true)
    private String code;

    @Column(name = "pattern", length = 100)
    private String pattern;
}
