package com.ej1.app.category.domain.service;

import com.ej1.app.category.domain.Category;
import com.ej1.app.category.domain.exception.CategoryNotFoundException;
import com.ej1.app.category.domain.exception.DuplicateCategoryNameException;
import com.ej1.app.category.domain.port.CategoryMapper;
import com.ej1.app.category.domain.port.CategoryRepository;
import com.ej1.app.category.domain.port.CreateCategoryCommand;
import com.ej1.app.category.domain.port.UpdateCategoryCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain service (use case) for the category master-data aggregate.
 * Lives in the domain layer; depends only on its own ports.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Category create(CreateCategoryCommand cmd) {
        if (repository.existsByNameIgnoreCase(cmd.name())) {
            throw new DuplicateCategoryNameException(cmd.name());
        }
        return repository.save(mapper.toDomain(cmd));
    }

    @Transactional
    public Category update(Long id, UpdateCategoryCommand cmd) {
        Category existing = findById(id);

        if (!existing.getName().equalsIgnoreCase(cmd.name()) && repository.existsByNameIgnoreCase(cmd.name())) {
            throw new DuplicateCategoryNameException(cmd.name());
        }

        Category updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new CategoryNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
