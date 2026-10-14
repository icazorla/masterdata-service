package io.masterdata.common.infrastructure.persistence;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base para toda entidad JPA que se identifica por un {@code id} autogenerado,
 * de forma que cada entidad ({@code Brand}, {@code Category}, {@code Product}, sus
 * traducciones, ...) declara la columna {@code id} una sola vez en lugar de
 * repetirla por clase.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
