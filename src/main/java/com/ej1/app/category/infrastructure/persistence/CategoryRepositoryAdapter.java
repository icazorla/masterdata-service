package com.ej1.app.category.infrastructure.persistence;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.port.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final CategoryEntityMapper mapper;

    @Override
    public Optional<Category> findById(Long id) {
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
    public List<Category> findAll() {
        return jpa.findAllByOrderByNameAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Category save(Category category) {
        return mapper.toDomain(jpa.save(mapper.toEntity(category)));
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
