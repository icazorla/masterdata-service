package com.ej1.app.shared.domain;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Base for domain aggregates carrying the standard {@code createdAt}/{@code updatedAt}
 * pair, so each aggregate (e.g. {@code Brand}, {@code Category}, {@code Product}) declares
 * and wires those two fields once instead of per class.
 */
@Getter
public abstract class Auditable {

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    protected Auditable(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
