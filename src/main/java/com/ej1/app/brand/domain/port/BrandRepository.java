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

    boolean existsByNameIgnoreCase(String name);

    /**
     * Returns every brand, sorted by name. Master data — no paging: the
     * table is expected to stay small (used to populate filters/selects).
     */
    List<Brand> findAll();

    Brand save(Brand brand);

    void deleteById(Long id);
}
