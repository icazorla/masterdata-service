package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.exception.InvalidCatalogQueryException;
import com.ej1.app.common.catalog.domain.port.CatalogMapper;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.port.CatalogTranslationCommand;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import com.ej1.app.common.domain.SortableFieldValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio de dominio (caso de uso) para todo agregado de datos maestros de
 * catálogo (marca, categoría, ...), que reemplaza al antiguo par
 * {@code BrandService}/{@code CategoryService} de un servicio por agregado.
 * Todo método público recibe un {@link CatalogType} (explícitamente, o vía el
 * comando en el caso de {@link #create}) y lo pasa directamente al único bean
 * {@link CatalogRepository} — ver su Javadoc para el porqué de que ahora solo
 * haya uno. Vive en la capa de dominio; depende únicamente de sus propios
 * puertos.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    /**
     * Nombres de campo comunes a todo catálogo que {@link #findAll} acepta para
     * {@code sortBy} — ver el {@code ALLOWED_SORT_FIELDS} análogo de {@code ProductService}
     * para el porqué de que esto viva aquí. {@code weight} es un entero manual, no
     * traducido — a diferencia de {@code name}/{@code description} no depende del locale
     * del cliente. Los campos adicionales propios de un tipo concreto (p. ej. {@code website}
     * solo para {@code brand}) también son ordenables, pero esos no son fijos — se resuelven
     * por tipo vía {@link CatalogRepository#sortableExtraFields} en {@link #findAll}.
     */
    static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "description", "weight");

    private final CatalogRepository repository;
    private final CatalogMapper mapper;

    @Transactional(readOnly = true)
    public Catalog findById(CatalogType type, Long id) {
        return repository.findById(type, id)
                .orElseThrow(() -> new CatalogNotFoundException(type, id));
    }

    @Transactional(readOnly = true)
    public boolean existsById(CatalogType type, Long id) {
        return repository.existsById(type, id);
    }

    /**
     * Lista filtrada y ordenada (en base de datos, ver {@link CatalogRepository#findAll})
     * de las entradas de {@code type}, resuelta para {@code locale}. Que {@code sortBy}
     * nombre un campo realmente ordenable de catálogo es conocimiento de negocio, así
     * que se comprueba aquí (422 si no lo es), igual que {@code ProductService#search} —
     * combinando los fijos ({@link #ALLOWED_SORT_FIELDS}) con los propios de {@code type}
     * ({@link CatalogRepository#sortableExtraFields}), así que {@code sortBy=website} es
     * válido para {@code brand} pero no para {@code category}.
     */
    @Transactional(readOnly = true)
    public List<Catalog> findAll(CatalogType type, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        Set<String> allowedFields = new LinkedHashSet<>(ALLOWED_SORT_FIELDS);
        allowedFields.addAll(repository.sortableExtraFields(type));
        SortableFieldValidator.requireSortable(sortBy, allowedFields, InvalidCatalogQueryException::new);
        return repository.findAll(type, locale, criteria, sortBy, sortDirection);
    }

    @Transactional
    public Catalog create(CreateCatalogCommand cmd) {
        requireTranslationsUnused(cmd.type(), cmd.translations(), null);
        return repository.save(mapper.toDomain(cmd));
    }

    @Transactional
    public Catalog update(CatalogType type, Long id, UpdateCatalogCommand cmd) {
        Catalog existing = findById(type, id);
        requireTranslationsUnused(type, cmd.translations(), id);

        Catalog updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    private void requireTranslationsUnused(CatalogType type, List<CatalogTranslationCommand> translations, Long excludeId) {
        for (CatalogTranslationCommand t : translations) {
            boolean nameTaken = excludeId == null
                    ? repository.existsByLocaleAndNameIgnoreCase(type, t.locale(), t.name())
                    : repository.existsByLocaleAndNameIgnoreCaseAndIdNot(type, t.locale(), t.name(), excludeId);
            if (nameTaken) {
                throw new DuplicateCatalogNameException(type, t.name());
            }
        }
    }

    @Transactional
    public void delete(CatalogType type, Long id) {
        if (!repository.existsById(type, id)) {
            throw new CatalogNotFoundException(type, id);
        }
        repository.deleteById(type, id);
    }
}
