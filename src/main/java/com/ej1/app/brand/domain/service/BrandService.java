package com.ej1.app.brand.domain.service;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.exception.BrandNotFoundException;
import com.ej1.app.brand.domain.exception.DuplicateBrandNameException;
import com.ej1.app.brand.domain.port.BrandMapper;
import com.ej1.app.brand.domain.port.BrandRepository;
import com.ej1.app.brand.domain.port.CreateBrandCommand;
import com.ej1.app.brand.domain.port.UpdateBrandCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain service (use case) for the brand master-data aggregate.
 * Lives in the domain layer; depends only on its own ports.
 */
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository repository;
    private final BrandMapper mapper;

    @Transactional(readOnly = true)
    public Brand findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Brand> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Brand create(CreateBrandCommand cmd) {
        if (repository.existsByNameIgnoreCase(cmd.name())) {
            throw new DuplicateBrandNameException(cmd.name());
        }
        return repository.save(mapper.toDomain(cmd));
    }

    @Transactional
    public Brand update(Long id, UpdateBrandCommand cmd) {
        Brand existing = findById(id);

        if (!existing.getName().equalsIgnoreCase(cmd.name()) && repository.existsByNameIgnoreCase(cmd.name())) {
            throw new DuplicateBrandNameException(cmd.name());
        }

        Brand updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BrandNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
