package com.ej1.app.brand.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    boolean existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCase(String locale, String name);

    boolean existsByTranslations_LocaleIgnoreCaseAndTranslations_NameIgnoreCaseAndIdNot(String locale, String name, Long id);

    /**
     * Ordered by {@code locale}'s translated name. Master data — no paging (see
     * {@link com.ej1.app.brand.domain.port.BrandRepository#findAll()}). Every brand
     * is required to carry the default locale's translation, so the adapter always
     * passes that one here. No {@code distinct} needed: {@code (brand_id, locale)}
     * is unique, so the join can return at most one row per brand.
     */
    @Query("select b from BrandEntity b join b.translations t where t.locale = :locale order by t.name asc")
    List<BrandEntity> findAllOrderByTranslatedName(String locale);
}
