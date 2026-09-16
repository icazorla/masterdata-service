package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.port.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryAdapter implements BrandRepository {

    private final BrandJpaRepository jpa;
    private final BrandEntityMapper mapper;

    @Override
    public Optional<Brand> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpa.existsByNameIgnoreCase(name);
    }

    @Override
    public List<Brand> findAll() {
        return jpa.findAllByOrderByNameAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Brand save(Brand brand) {
        return mapper.toDomain(jpa.save(mapper.toEntity(brand)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
