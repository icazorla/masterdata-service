package com.ej1.app.brand.domain.port;

/**
 * Command object representing the intent to update an existing brand.
 * Kept separate from {@link CreateBrandCommand} so the use case signature
 * is explicit and the contract for updates can evolve independently.
 */
public record UpdateBrandCommand(
        String name,
        String description
) {
}
