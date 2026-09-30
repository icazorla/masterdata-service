package com.ej1.app.common.domain;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Base para los agregados de dominio que, además del {@code id} de {@link Identifiable}, llevan
 * el par estándar {@code createdAt}/{@code updatedAt}, de forma que cada agregado (p. ej.
 * {@code Catalog}, {@code Product}) declara y configura esos campos una sola vez en lugar de
 * repetirlos por clase.
 */
@Getter
public abstract class Auditable extends Identifiable {

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    protected Auditable(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        super(id);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
