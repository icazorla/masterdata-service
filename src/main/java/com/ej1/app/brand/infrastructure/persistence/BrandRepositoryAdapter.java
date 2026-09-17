package com.ej1.app.brand.infrastructure.persistence;

import com.ej1.app.brand.domain.Brand;
import com.ej1.app.brand.domain.BrandTranslation;
import com.ej1.app.brand.domain.port.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryAdapter implements BrandRepository {

    private final BrandJpaRepository jpa;
    private final BrandEntityMapper mapper;

    @Value("${spring.web.locale:en}")
    private String defaultLocale;

    @Override
    public Optional<Brand> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCase(String locale, String name) {
        return jpa.existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCase(locale, name);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCaseAndIdNot(String locale, String name, Long excludeId) {
        return jpa.existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCaseAndIdNot(locale, name, excludeId);
    }

    @Override
    public List<Brand> findAll() {
        return jpa.findAllOrderByTranslatedName(defaultLocale).stream().map(mapper::toDomain).toList();
    }

    /**
     * On create, {@code mapper.toEntity} builds the whole entity (translations
     * included) from scratch. On update, translations are merged in-place onto
     * the already-persisted collection (see {@link #mergeTranslations}) instead
     * of being replaced wholesale — swapping out a persisted row for a brand-new
     * one with the same {@code (locale, name)} would violate that pair's unique
     * constraint the moment both exist, even transiently, within the same flush.
     */
    @Override
    public Brand save(Brand brand) {
        BrandEntity entity = brand.getId() == null ? mapper.toEntity(brand) : mergeTranslations(brand);
        return mapper.toDomain(jpa.save(entity));
    }

    private BrandEntity mergeTranslations(Brand brand) {
        BrandEntity existing = jpa.findById(brand.getId())
                .orElseThrow(() -> new IllegalStateException("Brand " + brand.getId() + " not found"));

        Map<String, BrandTranslationEntity> byLocale = existing.getTranslations().stream()
                .collect(Collectors.toMap(BrandTranslationEntity::getLocale, t -> t));
        Set<String> incomingLocales = brand.getTranslations().stream()
                .map(BrandTranslation::locale)
                .collect(Collectors.toSet());

        existing.getTranslations().removeIf(t -> !incomingLocales.contains(t.getLocale()));

        for (BrandTranslation t : brand.getTranslations()) {
            BrandTranslationEntity row = byLocale.get(t.locale());
            if (row != null) {
                row.setName(t.name());
                row.setDescription(t.description());
            } else {
                existing.getTranslations().add(BrandTranslationEntity.builder()
                        .brand(existing)
                        .locale(t.locale())
                        .name(t.name())
                        .description(t.description())
                        .build());
            }
        }
        return existing;
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
