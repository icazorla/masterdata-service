package com.ej1.app.category.domain.port;

import com.ej1.app.category.domain.Category;

import java.util.List;
import java.util.Optional;

/**
 * Outgoing port for category persistence. The domain defines what it needs
 * from the outside world; infrastructure adapters implement this contract.
 */
public interface CategoryRepository {

    Optional<Category> findById(Long id);

    boolean existsById(Long id);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Returns every category, sorted by name. Master data — no paging: the
     * table is expected to stay small (used to populate filters/selects).
     */
    List<Category> findAll();

    Category save(Category category);

    void deleteById(Long id);
}
