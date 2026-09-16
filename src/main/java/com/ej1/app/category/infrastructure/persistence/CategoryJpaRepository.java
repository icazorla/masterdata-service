package com.ej1.app.category.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<CategoryEntity> findAllByOrderByNameAsc();
}
