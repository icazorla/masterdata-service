package com.ej1.app.common.domain;

import lombok.Getter;

/**
 * Base para los agregados de dominio que se identifican por un {@code id}, de forma
 * que cada agregado (p. ej. {@code Catalog}, {@code Product}) declara ese campo una
 * sola vez en lugar de repetirlo por clase.
 */
@Getter
public abstract class Identifiable {

    private final Long id;

    protected Identifiable(Long id) {
        this.id = id;
    }
}
