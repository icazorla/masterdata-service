package com.ej1.app.brand.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<BrandEntity> findAllByOrderByNameAsc();
}
