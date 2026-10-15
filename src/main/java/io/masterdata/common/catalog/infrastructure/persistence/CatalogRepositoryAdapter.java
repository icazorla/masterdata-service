package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.exception.InvalidCatalogQueryException;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.domain.PageResult;
import io.masterdata.common.domain.Paging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Única implementación de {@link CatalogRepository} para todos los {@link CatalogType},
 * construida directamente sobre {@link EntityManager} en vez de un repositorio Spring
 * Data por cada tipo.
 *
 * <p>{@link #descriptors} (ver {@code CatalogPersistenceConfig}) es lo único aquí que
 * varía según el tipo — un token {@link Class}, no comportamiento. Todos los
 * métodos de abajo operan sobre una entidad únicamente a través de su superclase
 * común {@link CatalogBaseEntity} y de
 * {@link CatalogTranslationValue}, común a todos los tipos, despachando a un método
 * auxiliar privado genérico mediante la captura de comodines de Java: cada método
 * público busca el {@code CatalogTypeDescriptor<?>} del tipo y lo pasa directamente
 * a un método privado parametrizado con {@code <E>},
 * dejando que el compilador capture los argumentos de tipo reales del comodín para
 * esa llamada — sin casts sin comprobar.
 *
 * <p>El JPQL de abajo se construye a partir de {@code Class#getSimpleName()} (el nombre
 * de entidad JPA, ya que ninguna de estas entidades lo sobrescribe con
 * {@code @Entity(name = ...)}) — siempre a partir de un {@link CatalogTypeDescriptor}
 * que registramos nosotros mismos, nunca desde la entrada de la petición, así que no
 * hay riesgo de inyección pese a la concatenación de cadenas. Las consultas de
 * existencia/listado hacen join desde la colección {@code translations} de la entidad
 * (un {@code @ElementCollection} heredado de {@link CatalogBaseEntity}; solo cambia la
 * tabla donde se guarda, ver {@link CatalogImplicitNamingStrategy}) — eso es lo que mantiene este JPQL
 * genuinamente agnóstico al tipo.
 */
@Repository
public class CatalogRepositoryAdapter implements CatalogRepository {

    @PersistenceContext
    private EntityManager em;

    @Value("${spring.web.locale:en}")
    private String defaultLocale;

    private final Map<CatalogType, CatalogTypeDescriptor<?>> descriptors;

    public CatalogRepositoryAdapter(Map<CatalogType, CatalogTypeDescriptor<?>> descriptors) {
        this.descriptors = descriptors;
    }

    @Override
    public Optional<Catalog> findById(CatalogType type, Long id) {
        return findByIdGeneric(descriptorFor(type), id);
    }

    @Override
    public boolean existsByLocaleAndName(CatalogType type, String locale, String name, Long excludeId) {
        return existsByNameGeneric(descriptorFor(type), locale, name, excludeId);
    }

    @Override
    public List<Catalog> findAll(CatalogType type, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        return findAllGeneric(descriptorFor(type), locale, criteria, sortBy, sortDirection);
    }

    @Override
    public PageResult<Catalog> findPage(CatalogType type, String locale, CatalogSearchCriteria criteria, Paging paging) {
        return findPageGeneric(descriptorFor(type), locale, criteria, paging);
    }

    @Override
    public Set<String> sortableExtraFields(CatalogType type) {
        return Set.copyOf(descriptorFor(type).extraFields());
    }

    @Override
    public Map<String, String> extraFieldTypes(CatalogType type) {
        CatalogTypeDescriptor<?> descriptor = descriptorFor(type);
        return CatalogExtraFieldsAccessor.dataTypes(descriptor.entityClass(), descriptor.extraFields());
    }

    @Override
    public Catalog save(Catalog catalog) {
        return saveGeneric(descriptorFor(catalog.getType()), catalog);
    }

    private CatalogTypeDescriptor<?> descriptorFor(CatalogType type) {
        CatalogTypeDescriptor<?> descriptor = descriptors.get(type);
        if (descriptor == null) {
            throw new IllegalStateException("No CatalogTypeDescriptor registered for type " + type);
        }
        return descriptor;
    }

    private <E extends CatalogBaseEntity> Optional<Catalog> findByIdGeneric(
            CatalogTypeDescriptor<E> d, Long id) {
        E entity = em.find(d.entityClass(), id);
        return Optional.ofNullable(entity).map(e -> toDomain(d, e));
    }

    private <E extends CatalogBaseEntity> boolean existsByNameGeneric(
            CatalogTypeDescriptor<E> d, String locale, String name, Long excludeId) {
        String jpql = "select count(e) from " + d.entityClass().getSimpleName() + " e join e.translations t "
                + "where upper(t.locale) = upper(:locale) and upper(t.name) = upper(:name)"
                + (excludeId != null ? " and e.id <> :excludeId" : "");
        TypedQuery<Long> query = em.createQuery(jpql, Long.class)
                .setParameter("locale", locale)
                .setParameter("name", name);
        if (excludeId != null) {
            query.setParameter("excludeId", excludeId);
        }
        return query.getSingleResult() > 0;
    }

    /**
     * Filtrado y ordenado en base de datos sobre la traducción <em>resuelta</em> para
     * {@code locale}: se hace join tanto a la traducción de {@code locale} ({@code tLocale},
     * left join — puede no existir) como a la del idioma por defecto ({@code tDefault},
     * join normal — toda entrada está obligada a tenerla), y tanto el filtro como el
     * {@code order by} de {@code name}/{@code description} usan
     * {@code coalesce(tLocale.<campo>, tDefault.<campo>)}: exactamente el mismo fallback
     * que aplica {@code Catalog#resolveTranslation} para decidir qué name/description se
     * le muestra a este cliente. Así el resultado siempre coincide con lo que ve el
     * cliente, incluso cuando esta entrada no tiene traducción propia para su locale.
     * {@code weight} y los campos adicionales propios del tipo ({@code extraFields}, p. ej.
     * {@code regionCode} para {@code province}) en cambio viven en la entidad padre, no en la
     * traducción — no se traducen por locale — así que su expresión de orden es simplemente
     * {@code e.<campo>}, sin {@code coalesce} ni depender de qué traducción se resolvió (ver
     * {@link #orderByExpression}). Sin paginación — ver {@link #findPageGeneric} para la
     * variante paginada, que comparte este mismo {@code from}/{@code where}
     * ({@link #fromWhereClause}).
     *
     * <p>{@code weight} manda siempre como primer criterio de orden, descendente, antes que
     * {@code sortBy} — ver {@link #orderByClauses}. Esto es deliberado: {@code weight} es un
     * peso de ordenación manual (p. ej. destacar una entrada), así que una entrada con peso
     * alto aparece siempre por delante sin importar por qué campo o dirección se ordene el
     * resto; {@code sortBy}/{@code sortDirection} solo deciden el desempate entre entradas
     * del mismo peso.
     *
     * <p>Todos los filtros ({@link CatalogSearchCriteria#filters}, ver {@link #filterCondition})
     * se combinan con AND, también {@code name} con {@code description}.
     *
     * <p>No hace falta {@code distinct}: {@code (parent id, locale)} es único, así que cada
     * join devuelve como máximo una fila por entrada.
     *
     * <p>{@code sortBy}/{@code sortDirection} llegan ya validados como campo ordenable/
     * ASC-DESC por {@code CatalogQueryService#findAll}; aun así se resuelven aquí a través de
     * {@link #orderByExpression}/{@link #sortDirectionSql} (en vez de concatenarlos
     * directamente en el JPQL) para que este método nunca dependa de esa validación
     * externa para ser seguro frente a inyección. {@code e.id} se añade como desempate
     * final para que el orden sea total y estable — imprescindible al paginar (sin él,
     * entradas con el mismo valor de {@code weight}/{@code sortBy} podrían repetirse o
     * perderse entre páginas), e inocuo sin paginar.
     */
    private <E extends CatalogBaseEntity> List<Catalog> findAllGeneric(
            CatalogTypeDescriptor<E> d, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        return selectQuery(d, locale, criteria, sortBy, sortDirection)
                .getResultList().stream().map(e -> toDomain(d, e)).toList();
    }

    /**
     * Mismo filtro/orden que {@link #findAllGeneric}, limitado a una página con
     * {@code setFirstResult}/{@code setMaxResults} (no hay fetch joins de colecciones, así
     * que el límite se aplica en SQL, no en memoria) más un {@code count} sobre el mismo
     * {@code from}/{@code where} para el total. El {@code count} solo se lanza cuando la
     * página no permite deducir el total por sí sola — p. ej. una primera página incompleta
     * ya es el total. Un offset que no cabe en un {@code int} (página absurdamente alta)
     * queda necesariamente fuera de rango: página vacía, sin lanzar la consulta de datos.
     */
    private <E extends CatalogBaseEntity> PageResult<Catalog> findPageGeneric(
            CatalogTypeDescriptor<E> d, String locale, CatalogSearchCriteria criteria, Paging paging) {
        long offset = (long) paging.page() * paging.size();
        List<Catalog> content = offset > Integer.MAX_VALUE
                ? List.of()
                : selectQuery(d, locale, criteria, paging.sortBy(), paging.sortDirection())
                        .setFirstResult((int) offset)
                        .setMaxResults(paging.size())
                        .getResultList().stream().map(e -> toDomain(d, e)).toList();

        long total;
        if (!content.isEmpty() && content.size() < paging.size()) {
            total = offset + content.size();
        } else {
            TypedQuery<Long> countQuery = em.createQuery("select count(e) " + fromWhereClause(d, criteria), Long.class);
            bindFilterParameters(countQuery, d, locale, criteria);
            total = countQuery.getSingleResult();
        }
        int totalPages = (int) ((total + paging.size() - 1) / paging.size());
        return new PageResult<>(content, paging.page(), paging.size(), total, totalPages);
    }

    private <E extends CatalogBaseEntity> TypedQuery<E> selectQuery(
            CatalogTypeDescriptor<E> d, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        String jpql = "select e " + fromWhereClause(d, criteria)
                + " order by " + orderByClauses(sortBy, sortDirection, d.extraFields())
                + ", e.id asc";
        TypedQuery<E> query = em.createQuery(jpql, d.entityClass());
        bindFilterParameters(query, d, locale, criteria);
        return query;
    }

    /**
     * {@code weight} es siempre el primer criterio de orden, descendente (un peso alto
     * destaca siempre primero), sin importar qué pida {@code sortDirection} — eso solo
     * gobierna el campo secundario, {@code sortBy}. Excepción: si {@code sortBy} ya es
     * {@code "weight"}, no hay un campo secundario distinto que añadir — en ese caso se
     * respeta directamente la dirección pedida para {@code weight} en lugar de forzar
     * descendente e ignorar un {@code sortDirection=ASC} explícito sobre ese mismo campo
     * (un segundo {@code "e.weight <dir>"} tras el {@code "e.weight desc"} inicial tampoco
     * podría reordenar nada: dentro de cada grupo de ese primer criterio todas las filas
     * comparten ya el mismo valor).
     */
    private static String orderByClauses(String sortBy, String sortDirection, List<String> extraFields) {
        if ("weight".equals(sortBy)) {
            return "e.weight " + sortDirectionSql(sortDirection);
        }
        return "e.weight desc, " + orderByExpression(sortBy, extraFields) + ' ' + sortDirectionSql(sortDirection);
    }

    /**
     * {@code from ... where ...} común a la consulta de datos y a la de {@code count} — ver
     * {@link #findAllGeneric} para el porqué de los dos joins y del {@code coalesce}.
     */
    private static String fromWhereClause(CatalogTypeDescriptor<?> d, CatalogSearchCriteria criteria) {
        StringBuilder jpql = new StringBuilder("from " + d.entityClass().getSimpleName() + " e "
                + "join e.translations tDefault "
                + "left join e.translations tLocale on tLocale.locale = :locale "
                + "where tDefault.locale = :defaultLocale");
        List<CatalogFilter> filters = criteria.filters();
        for (int i = 0; i < filters.size(); i++) {
            jpql.append(" and ").append(filterCondition(d, filters.get(i), i));
        }
        return jpql.toString();
    }

    /**
     * Condición JPQL del {@code index}-ésimo filtro, con parámetro {@code :f<index>} (o
     * {@code :f<index>_<n>}, uno por valor, en texto) que rellena {@link #bindFilter}. En texto
     * cada valor es un "contiene" sin distinguir mayúsculas y varios valores se combinan con OR;
     * en el resto de tipos, un valor es igualdad, varios son {@code in}, y los operadores de rango
     * comparan con su único valor. La columna sale de {@link #filterColumn}, que solo acepta
     * campos conocidos de ESTE tipo — igual que {@link #orderByExpression}, este método no depende
     * de la validación de {@code CatalogQueryService} para ser seguro frente a inyección.
     */
    private static String filterCondition(CatalogTypeDescriptor<?> d, CatalogFilter filter, int index) {
        String column = filterColumn(d, filter.field());
        String param = ":f" + index;
        return switch (filter.operator()) {
            case GT -> column + " > " + param;
            case GTE -> column + " >= " + param;
            case LT -> column + " < " + param;
            case LTE -> column + " <= " + param;
            case EQ -> {
                if (isTextFilter(d, filter.field())) {
                    List<String> likes = new ArrayList<>();
                    for (int n = 0; n < filter.values().size(); n++) {
                        likes.add("upper(" + column + ") like upper(" + param + '_' + n + ")");
                    }
                    yield "(" + String.join(" or ", likes) + ")";
                }
                yield filter.values().size() == 1 ? column + " = " + param : column + " in (" + param + ")";
            }
        };
    }

    /**
     * Expresión JPQL de un campo filtrable: {@code name}/{@code description} sobre la traducción
     * resuelta ({@code coalesce}, ver {@link #findAllGeneric}), {@code enabled} y los campos
     * adicionales de este tipo sobre la propia entidad.
     */
    private static String filterColumn(CatalogTypeDescriptor<?> d, String field) {
        return switch (field) {
            case CatalogFilter.NAME -> "coalesce(tLocale.name, tDefault.name)";
            case CatalogFilter.DESCRIPTION -> "coalesce(tLocale.description, tDefault.description)";
            case CatalogFilter.ENABLED -> "e.enabled";
            default -> {
                if (!d.extraFields().contains(field)) {
                    throw new IllegalArgumentException("Not a filterable field of " + d.type() + ": " + field);
                }
                yield "e." + field;
            }
        };
    }

    private static boolean isTextFilter(CatalogTypeDescriptor<?> d, String field) {
        return switch (field) {
            case CatalogFilter.NAME, CatalogFilter.DESCRIPTION -> true;
            default -> CatalogExtraFieldsAccessor.isText(d.entityClass(), field);
        };
    }

    private void bindFilterParameters(Query query, CatalogTypeDescriptor<?> d, String locale, CatalogSearchCriteria criteria) {
        query.setParameter("locale", locale);
        query.setParameter("defaultLocale", defaultLocale);
        List<CatalogFilter> filters = criteria.filters();
        for (int i = 0; i < filters.size(); i++) {
            bindFilter(query, d, filters.get(i), i);
        }
    }

    /**
     * Rellena los parámetros de {@link #filterCondition}: en texto, cada valor envuelto en
     * {@code %}; en el resto, convertido al tipo Java real de la columna (422 si no se puede).
     */
    private static void bindFilter(Query query, CatalogTypeDescriptor<?> d, CatalogFilter filter, int index) {
        String param = "f" + index;
        if (filter.operator() == CatalogFilter.Operator.EQ && isTextFilter(d, filter.field())) {
            for (int n = 0; n < filter.values().size(); n++) {
                query.setParameter(param + '_' + n, "%" + filter.values().get(n) + "%");
            }
            return;
        }
        List<Object> values = filter.values().stream()
                .map(value -> convertFilterValue(d, filter.field(), value))
                .toList();
        query.setParameter(param, values.size() == 1 ? values.get(0) : values);
    }

    /** {@code enabled} es una propiedad más de la entidad (heredada), así que se convierte igual que un campo adicional. */
    private static Object convertFilterValue(CatalogTypeDescriptor<?> d, String field, String value) {
        try {
            return CatalogExtraFieldsAccessor.convert(d.entityClass(), field, value);
        } catch (IllegalArgumentException e) {
            throw InvalidCatalogQueryException.invalidFilterValue(field, value,
                    CatalogExtraFieldsAccessor.dataType(d.entityClass(), field));
        }
    }

    /**
     * {@code extraFields} es la lista de {@link CatalogTypeDescriptor#extraFields()} del tipo
     * que se está consultando — {@code sortBy} solo cae en la rama {@code default} para
     * {@code name}/{@code description}/{@code weight}, así que aquí basta con comprobar que
     * nombre uno de los campos extra de ESTE tipo (p. ej. {@code regionCode} es
     * válido para {@code province} pero no para {@code region}) antes de aceptarlo como
     * columna — sin este chequeo, un {@code sortBy} arbitrario que no fuera ninguno de los
     * tres fijos ni un extra conocido acabaría concatenado igualmente en el JPQL.
     */
    private static String orderByExpression(String sortBy, List<String> extraFields) {
        return switch (sortBy) {
            case "name" -> "coalesce(tLocale.name, tDefault.name)";
            case "description" -> "coalesce(tLocale.description, tDefault.description)";
            case "weight" -> "e.weight";
            default -> {
                if (extraFields.contains(sortBy)) {
                    yield "e." + sortBy;
                }
                throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
            }
        };
    }

    private static String sortDirectionSql(String sortDirection) {
        return "DESC".equalsIgnoreCase(sortDirection) ? "desc" : "asc";
    }

    /**
     * En la creación, {@link #createEntity} construye la entidad completa (traducciones
     * incluidas) desde cero. En la actualización, {@code weight}, {@code enabled}, los campos
     * adicionales y las traducciones se escriben sobre la entidad ya persistida y todavía
     * gestionada (ver {@link #mergeIntoExisting}) — obtenida mediante {@code em.find} dentro
     * de la transacción actual, así que basta con mutar sus campos; no hace falta ninguna
     * llamada explícita a {@code merge}/{@code persist} en el camino de actualización, el
     * dirty checking la vuelca al hacer commit. Las traducciones simplemente se sustituyen:
     * son valores ({@link CatalogTranslationValue}) sin identidad, y Hibernate reescribe un
     * {@code @ElementCollection} borrando primero todas sus filas y reinsertándolas después,
     * así que reutilizar un mismo {@code (locale, name)} nunca choca con la restricción de
     * unicidad de ese par.
     */
    private <E extends CatalogBaseEntity> Catalog saveGeneric(
            CatalogTypeDescriptor<E> d, Catalog catalog) {
        E entity;
        if (catalog.getId() == null) {
            entity = createEntity(d, catalog);
            em.persist(entity);
        } else {
            entity = mergeIntoExisting(d, catalog);
        }
        em.flush(); // la respuesta lleva ya la nueva version y updatedAt
        return toDomain(d, entity);
    }

    private <E extends CatalogBaseEntity> E createEntity(
            CatalogTypeDescriptor<E> d, Catalog catalog) {
        E entity = newInstance(d.entityClass());
        entity.setWeight(catalog.getWeight());
        entity.setEnabled(catalog.isEnabled());
        CatalogExtraFieldsAccessor.write(entity, d.extraFields(), catalog.getExtraFields());
        entity.getTranslations().addAll(toTranslationValues(catalog));
        return entity;
    }

    private <E extends CatalogBaseEntity> E mergeIntoExisting(
            CatalogTypeDescriptor<E> d, Catalog catalog) {
        E existing = em.find(d.entityClass(), catalog.getId());
        if (existing == null) {
            throw new IllegalStateException(d.entityClass().getSimpleName() + " " + catalog.getId() + " not found");
        }

        existing.setWeight(catalog.getWeight());
        existing.setEnabled(catalog.isEnabled());
        CatalogExtraFieldsAccessor.write(existing, d.extraFields(), catalog.getExtraFields());

        existing.getTranslations().clear();
        existing.getTranslations().addAll(toTranslationValues(catalog));
        return existing;
    }

    private static List<CatalogTranslationValue> toTranslationValues(Catalog catalog) {
        return catalog.getTranslations().stream()
                .map(t -> new CatalogTranslationValue(t.locale(), t.name(), t.description()))
                .toList();
    }

    private <E extends CatalogBaseEntity> Catalog toDomain(
            CatalogTypeDescriptor<E> d, E entity) {
        return Catalog.builder()
                .id(entity.getId())
                .type(d.type())
                .translations(entity.getTranslations().stream()
                        .map(t -> new CatalogTranslation(t.getLocale(), t.getName(), t.getDescription()))
                        .toList())
                .weight(entity.getWeight() != null ? entity.getWeight() : 0)
                .enabled(!Boolean.FALSE.equals(entity.getEnabled()))
                .extraFields(CatalogExtraFieldsAccessor.read(entity, d.extraFields()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + clazz.getName(), e);
        }
    }
}
