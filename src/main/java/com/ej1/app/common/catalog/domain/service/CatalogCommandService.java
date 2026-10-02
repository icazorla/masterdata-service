package com.ej1.app.common.catalog.domain.service;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.exception.DuplicateCatalogNameException;
import com.ej1.app.common.catalog.domain.port.CatalogMapper;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogTranslationCommand;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de <b>escritura</b> de todo catálogo de datos maestros: la mitad "command"
 * del par {@link CatalogQueryService}/{@link CatalogCommandService}. Solo lo usa la API de
 * gestión ({@code /admin/api/catalogs}) — la de consulta no tiene acceso a él, así que no
 * puede modificar nada por construcción. Aquí viven las reglas de negocio de escritura
 * (nombre único por tipo y locale). Vive en la capa de dominio; depende únicamente de sus
 * propios puertos.
 *
 * <p><b>Caché</b>: cada escritura invalida lo que {@link CatalogQueryService} cachea para
 * <em>su</em> tipo ({@link CatalogCacheNames}; las cachés de los demás tipos no se tocan) — la
 * entrada por id afectada y <em>todos</em> los listados del tipo, ya que un cambio puede mover la
 * entrada en cualquiera de ellos (nombre, orden, peso) y sus claves no se pueden enumerar. Las
 * escrituras son raras frente a las lecturas, así que vaciarlos es barato. La invalidación se
 * aplica al hacer commit (ver {@code CatalogCacheConfig}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@CacheConfig(cacheResolver = CatalogCacheNames.RESOLVER)
public class CatalogCommandService {

    private final CatalogRepository repository;
    private final CatalogMapper mapper;

    @CacheEvict(cacheNames = CatalogCacheNames.LISTS, allEntries = true)
    public Catalog create(CreateCatalogCommand cmd) {
        requireTranslationsUnused(cmd.type(), cmd.translations(), null);
        return repository.save(mapper.toDomain(cmd));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CatalogCacheNames.BY_ID, key = CatalogCacheNames.BY_ID_KEY),
            @CacheEvict(cacheNames = CatalogCacheNames.LISTS, allEntries = true)
    })
    public Catalog update(CatalogType type, Long id, UpdateCatalogCommand cmd) {
        Catalog existing = repository.findById(type, id)
                .orElseThrow(() -> new CatalogNotFoundException(type, id));
        requireTranslationsUnused(type, cmd.translations(), id);

        Catalog updated = mapper.applyUpdate(existing, cmd);
        return repository.save(updated);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CatalogCacheNames.BY_ID, key = CatalogCacheNames.BY_ID_KEY),
            @CacheEvict(cacheNames = CatalogCacheNames.LISTS, allEntries = true)
    })
    public void delete(CatalogType type, Long id) {
        if (!repository.existsById(type, id)) {
            throw new CatalogNotFoundException(type, id);
        }
        repository.deleteById(type, id);
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
}
