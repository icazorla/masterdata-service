package com.ej1.app.brand.domain.port;

import com.ej1.app.brand.domain.Brand;

import java.util.List;
import java.util.Optional;

/**
 * Outgoing port for brand persistence. The domain defines what it needs
 * from the outside world; infrastructure adapters implement this contract.
 */
public interface BrandRepository {

    Optional<Brand> findById(Long id);

    boolean existsById(Long id);

    /** Whether some brand already has {@code name} (case-insensitive) for {@code locale}. */
    boolean existsByLocaleAndNameIgnoreCase(String locale, String name);

    /** Same as {@link #existsByLocaleAndNameIgnoreCase}, ignoring the brand being updated ({@code excludeId}). */
    boolean existsByLocaleAndNameIgnoreCaseAndIdNot(String locale, String name, Long excludeId);

    /**
     * Returns every brand, sorted by name. Master data — no paging: the
     * table is expected to stay small (used to populate filters/selects).
     */
    List<Brand> findAll();

    Brand save(Brand brand);

    void deleteById(Long id);
}
