package io.masterdata.common.catalog.domain.service;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogField;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.CatalogTypeDefinition;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import io.masterdata.common.domain.SortableFieldValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casos de uso de <b>lectura</b> de todo catálogo de datos maestros (marca, categoría, ...):
 * la mitad "query" del par {@link CatalogQueryService}/{@link CatalogCommandService} (CQRS
 * ligero). Lo usan tanto la API de consulta ({@code /api/catalogs}) como las lecturas de la
 * API de gestión ({@code /admin/api/catalogs}); ningún método aquí modifica estado, así que
 * todos corren en transacciones {@code readOnly}. Todo método recibe un {@link CatalogType}
 * explícito y lo pasa directamente al único bean {@link CatalogRepository}. Vive en la capa
 * de dominio; depende únicamente de sus propios puertos.
 *
 * <p><b>Caché</b> ({@link CatalogCacheNames}): {@link #findTypeDefinitions}, {@link #findById} y
 * los {@link #findAll} que no filtran (o solo por {@code enabled}) se sirven desde caché — las dos últimas en cachés propias
 * de cada {@link CatalogType} (con su propia configuración), elegidas por el resolver de
 * {@code @CacheConfig}; {@link CatalogCommandService} las invalida en cada escritura.
 * {@link #findPage} y los listados filtrados no se cachean: sus combinaciones (texto libre ×
 * página × tamaño) apenas se repiten y solo inflarían la caché.
 * Los {@link Catalog} cacheados son inmutables, así que compartir la instancia es seguro.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@CacheConfig(cacheResolver = CatalogCacheNames.RESOLVER)
public class CatalogQueryService {

    /**
     * Nombres de campo comunes a todo catálogo que {@link #findAll} acepta para
     * {@code sortBy}. {@code weight} es un entero manual, no traducido — a diferencia de
     * {@code name}/{@code description} no depende del locale del cliente. Los campos
     * adicionales propios de un tipo concreto (p. ej. {@code website} solo para
     * {@code brand}) también son ordenables, pero esos no son fijos — se resuelven por tipo
     * vía {@link CatalogRepository#sortableExtraFields} en {@link #findAll}.
     */
    static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "description", "weight");

    /**
     * Campos comunes a todo catálogo, tal y como los ve la API (ver {@code CatalogRequest}/
     * {@code CatalogResponse}): los que expone {@link #findTypeDefinitions} antes de los
     * adicionales de cada tipo. {@code sortable} coincide con {@link #ALLOWED_SORT_FIELDS}, y los
     * que tienen {@code filterOperators} son {@link CatalogFilter#COMMON_FIELDS}.
     */
    static final List<CatalogField> COMMON_FIELDS = List.of(
            new CatalogField("id", "integer", false, true, false, false, false),
            new CatalogField("name", "string", true, false, true, true, false, List.of("contains")),
            new CatalogField("description", "string", false, false, true, true, false, List.of("contains")),
            new CatalogField("weight", "integer", false, false, false, true, false),
            new CatalogField("enabled", "boolean", false, false, false, false, false, List.of("eq")),
            new CatalogField("createdAt", "date-time", false, true, false, false, false),
            new CatalogField("updatedAt", "date-time", false, true, false, false, false));

    private final CatalogRepository repository;

    /**
     * Todos los tipos de catálogo configurados ({@link CatalogType#values()}, en el orden de
     * {@code application.yml}) con la definición de sus campos: {@link #COMMON_FIELDS} seguidos
     * de los adicionales propios de cada tipo ({@link CatalogRepository#extraFieldTypes}).
     */
    @Cacheable(CatalogCacheNames.TYPES)
    public List<CatalogTypeDefinition> findTypeDefinitions() {
        return CatalogType.values().stream()
                .map(type -> {
                    List<CatalogField> fields = new ArrayList<>(COMMON_FIELDS);
                    repository.extraFieldTypes(type).forEach((name, dataType) -> fields.add(CatalogField.extra(name, dataType)));
                    return new CatalogTypeDefinition(type, List.copyOf(fields));
                })
                .toList();
    }

    @Cacheable(cacheNames = CatalogCacheNames.BY_ID, key = CatalogCacheNames.BY_ID_KEY)
    public Catalog findById(CatalogType type, Long id) {
        return repository.findById(type, id)
                .orElseThrow(() -> new CatalogNotFoundException(type, id));
    }

    public boolean existsById(CatalogType type, Long id) {
        return repository.existsById(type, id);
    }

    /**
     * Lista filtrada y ordenada (en base de datos, ver {@link CatalogRepository#findAll})
     * de las entradas de {@code type}, resuelta para {@code locale}. Que {@code sortBy}
     * nombre un campo realmente ordenable de catálogo es conocimiento de negocio, así
     * que se comprueba aquí (422 si no lo es) — combinando los fijos
     * ({@link #ALLOWED_SORT_FIELDS}) con los propios de {@code type}
     * ({@link CatalogRepository#sortableExtraFields}), así que {@code sortBy=website} es
     * válido para {@code brand} pero no para {@code category}.
     *
     * <p>Los filtros ({@link CatalogSearchCriteria#filters}) también se validan aquí: cada uno debe
     * nombrar un campo filtrable de {@code type} (uno común de {@link CatalogFilter#COMMON_FIELDS} o
     * uno adicional del tipo) y usar un operador válido para su tipo de dato (los de rango solo
     * para números y fechas) — 422 si no.
     *
     * <p>Solo se cachea sin filtros (o filtrando solo por {@code enabled}), la llamada típica para
     * poblar selects; la caché es la de {@code type} y la clave (type, locale, criteria, sortBy,
     * sortDirection).
     * Un {@code sortBy} o filtro inválido lanza antes de cachear nada.
     */
    @Cacheable(cacheNames = CatalogCacheNames.LISTS,
            condition = "#criteria.filtersOnlyByEnabled()")
    public List<Catalog> findAll(CatalogType type, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        requireSortable(type, sortBy);
        requireFilterable(type, criteria);
        return repository.findAll(type, locale, criteria, sortBy, sortDirection);
    }

    /**
     * Igual que {@link #findAll} (mismo filtro, orden y validación de {@code sortBy} y filtros), pero
     * devolviendo solo la página pedida en {@code paging} — ver {@link CatalogRepository#findPage}.
     */
    public PageResult<Catalog> findPage(CatalogType type, String locale, CatalogSearchCriteria criteria, Paging paging) {
        requireSortable(type, paging.sortBy());
        requireFilterable(type, criteria);
        return repository.findPage(type, locale, criteria, paging);
    }

    private void requireSortable(CatalogType type, String sortBy) {
        Set<String> allowedFields = new LinkedHashSet<>(ALLOWED_SORT_FIELDS);
        allowedFields.addAll(repository.sortableExtraFields(type));
        SortableFieldValidator.requireSortable(sortBy, allowedFields, InvalidCatalogQueryException::new);
    }

    private void requireFilterable(CatalogType type, CatalogSearchCriteria criteria) {
        if (criteria.filters().isEmpty()) {
            return;
        }
        Map<String, String> dataTypes = new LinkedHashMap<>();
        COMMON_FIELDS.stream()
                .filter(f -> CatalogFilter.COMMON_FIELDS.contains(f.name()))
                .forEach(f -> dataTypes.put(f.name(), f.dataType()));
        dataTypes.putAll(repository.extraFieldTypes(type));
        for (CatalogFilter filter : criteria.filters()) {
            String dataType = dataTypes.get(filter.field());
            if (dataType == null) {
                throw InvalidCatalogQueryException.unknownFilterField(filter.field(), dataTypes.keySet());
            }
            if (!filter.operator().supports(dataType)) {
                throw InvalidCatalogQueryException.unsupportedFilterOperator(filter.key(), dataType);
            }
        }
    }
}
