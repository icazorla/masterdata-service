package com.ej1.app.common.catalog.infrastructure.persistence;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Única implementación de {@link CatalogRepository} para todos los {@link CatalogType},
 * construida directamente sobre {@link EntityManager} en vez de un repositorio Spring
 * Data + un mapeador de entidad MapStruct + una subclase adaptadora por cada tipo
 * (lo que esto reemplaza).
 *
 * <p>{@link #descriptors} (ver {@code CatalogPersistenceConfig}) es lo único aquí que
 * varía según el tipo — un par de tokens {@link Class}, no comportamiento. Todos los
 * métodos de abajo operan sobre una entidad únicamente a través de los contratos
 * estructurales {@link CatalogEntity}/{@link CatalogTranslationEntity} (ya satisfechos
 * por {@code BrandEntity}/{@code CategoryEntity} mediante sus accesores generados por
 * Lombok), despachando a un método auxiliar privado genérico mediante la captura de
 * comodines de Java: cada método público busca el {@code CatalogTypeDescriptor<?, ?>}
 * del tipo y lo pasa directamente a un método privado parametrizado con {@code <E, TE>},
 * dejando que el compilador capture los argumentos de tipo reales del comodín para
 * esa llamada — sin casts sin comprobar.
 *
 * <p>El JPQL de abajo se construye a partir de {@code Class#getSimpleName()} (el nombre
 * de entidad JPA, ya que ninguna de estas entidades lo sobrescribe con
 * {@code @Entity(name = ...)}) — siempre a partir de un {@link CatalogTypeDescriptor}
 * que registramos nosotros mismos, nunca desde la entrada de la petición, así que no
 * hay riesgo de inyección pese a la concatenación de cadenas. Las consultas de
 * existencia/listado hacen join desde la colección {@code translations} de la entidad
 * *padre* (un nombre de campo que comparten todas las entidades de tipo catálogo) en
 * vez de navegar la referencia inversa de una fila de traducción hacia su padre
 * (nombrada de forma distinta según el tipo: {@code brand}/{@code category}) — eso es
 * lo que mantiene este JPQL genuinamente agnóstico al tipo.
 */
@Repository
public class CatalogRepositoryAdapter implements CatalogRepository {

    @PersistenceContext
    private EntityManager em;

    @Value("${spring.web.locale:en}")
    private String defaultLocale;

    private final Map<CatalogType, CatalogTypeDescriptor<?, ?>> descriptors;

    public CatalogRepositoryAdapter(Map<CatalogType, CatalogTypeDescriptor<?, ?>> descriptors) {
        this.descriptors = descriptors;
    }

    @Override
    public Optional<Catalog> findById(CatalogType type, Long id) {
        return findByIdGeneric(descriptorFor(type), id);
    }

    @Override
    public boolean existsById(CatalogType type, Long id) {
        return existsByIdGeneric(descriptorFor(type), id);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCase(CatalogType type, String locale, String name) {
        return existsByNameGeneric(descriptorFor(type), locale, name, null);
    }

    @Override
    public boolean existsByLocaleAndNameIgnoreCaseAndIdNot(CatalogType type, String locale, String name, Long excludeId) {
        return existsByNameGeneric(descriptorFor(type), locale, name, excludeId);
    }

    @Override
    public List<Catalog> findAll(CatalogType type, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        return findAllGeneric(descriptorFor(type), locale, criteria, sortBy, sortDirection);
    }

    @Override
    public Set<String> sortableExtraFields(CatalogType type) {
        return Set.copyOf(descriptorFor(type).extraFields());
    }

    @Override
    public Catalog save(Catalog catalog) {
        return saveGeneric(descriptorFor(catalog.getType()), catalog);
    }

    @Override
    public void deleteById(CatalogType type, Long id) {
        deleteByIdGeneric(descriptorFor(type), id);
    }

    private CatalogTypeDescriptor<?, ?> descriptorFor(CatalogType type) {
        CatalogTypeDescriptor<?, ?> descriptor = descriptors.get(type);
        if (descriptor == null) {
            throw new IllegalStateException("No CatalogTypeDescriptor registered for type " + type);
        }
        return descriptor;
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> Optional<Catalog> findByIdGeneric(
            CatalogTypeDescriptor<E, TE> d, Long id) {
        E entity = em.find(d.entityClass(), id);
        return Optional.ofNullable(entity).map(e -> toDomain(d, e));
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> boolean existsByIdGeneric(
            CatalogTypeDescriptor<E, TE> d, Long id) {
        String jpql = "select count(e) from " + d.entityClass().getSimpleName() + " e where e.id = :id";
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult() > 0;
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> boolean existsByNameGeneric(
            CatalogTypeDescriptor<E, TE> d, String locale, String name, Long excludeId) {
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
     * {@code website} para {@code brand}) en cambio viven en la entidad padre, no en la
     * traducción — no se traducen por locale — así que su expresión de orden es simplemente
     * {@code e.<campo>}, sin {@code coalesce} ni depender de qué traducción se resolvió (ver
     * {@link #orderByExpression}). Datos maestros — sin paginación (ver
     * {@link CatalogRepository#findAll}).
     *
     * <p>Cuando se dan {@code name} y {@code description} a la vez se combinan con OR
     * (coincide si el nombre o la descripción contienen el filtro), no con AND — cada uno
     * es una condición independiente, no una restricción adicional sobre la otra.
     *
     * <p>No hace falta {@code distinct}: {@code (parent id, locale)} es único, así que cada
     * join devuelve como máximo una fila por entrada.
     *
     * <p>{@code sortBy}/{@code sortDirection} llegan ya validados como campo ordenable/
     * ASC-DESC por {@code CatalogService#findAll}; aun así se resuelven aquí a través de
     * {@link #orderByExpression}/{@link #sortDirectionSql} (en vez de concatenarlos
     * directamente en el JPQL) para que este método nunca dependa de esa validación
     * externa para ser seguro frente a inyección.
     */
    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> List<Catalog> findAllGeneric(
            CatalogTypeDescriptor<E, TE> d, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection) {
        StringBuilder jpql = new StringBuilder("select e from " + d.entityClass().getSimpleName() + " e "
                + "join e.translations tDefault "
                + "left join e.translations tLocale on tLocale.locale = :locale "
                + "where tDefault.locale = :defaultLocale");
        List<String> filterConditions = new ArrayList<>();
        if (criteria.name() != null) {
            filterConditions.add("upper(coalesce(tLocale.name, tDefault.name)) like upper(:name)");
        }
        if (criteria.description() != null) {
            filterConditions.add("upper(coalesce(tLocale.description, tDefault.description)) like upper(:description)");
        }
        if (!filterConditions.isEmpty()) {
            jpql.append(" and (").append(String.join(" or ", filterConditions)).append(")");
        }
        jpql.append(" order by ").append(orderByExpression(sortBy, d.extraFields())).append(' ').append(sortDirectionSql(sortDirection));

        TypedQuery<E> query = em.createQuery(jpql.toString(), d.entityClass())
                .setParameter("locale", locale)
                .setParameter("defaultLocale", defaultLocale);
        if (criteria.name() != null) {
            query.setParameter("name", "%" + criteria.name() + "%");
        }
        if (criteria.description() != null) {
            query.setParameter("description", "%" + criteria.description() + "%");
        }
        return query.getResultList().stream().map(e -> toDomain(d, e)).toList();
    }

    /**
     * {@code extraFields} es la lista de {@link CatalogTypeDescriptor#extraFields()} del tipo
     * que se está consultando — {@code sortBy} solo cae en la rama {@code default} para
     * {@code name}/{@code description}/{@code weight}, así que aquí basta con comprobar que
     * nombre uno de los campos extra configurados para ESTE tipo (p. ej. {@code website} es
     * válido para {@code brand} pero no para {@code category}) antes de aceptarlo como
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
     * incluidas) desde cero. En la actualización, {@code weight} y las traducciones se
     * fusionan in situ sobre la entidad ya persistida y todavía gestionada (ver
     * {@link #mergeIntoExisting}) — obtenida mediante {@code em.find} dentro de la
     * transacción actual, así que basta con mutar sus campos; no hace falta ninguna
     * llamada explícita a {@code merge}/{@code persist} en el camino de actualización, el
     * dirty checking la vuelca al hacer commit. Fusionar las traducciones in situ (en vez
     * de reemplazar la colección por completo) importa por la misma razón que en el
     * antiguo adaptador de Spring Data: sustituir una fila de traducción persistida por
     * una nueva con el mismo {@code (locale, name)} violaría la restricción de unicidad
     * de ese par en el momento en que ambas existieran, aunque fuera transitoriamente,
     * dentro del mismo flush.
     */
    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> Catalog saveGeneric(
            CatalogTypeDescriptor<E, TE> d, Catalog catalog) {
        E entity;
        if (catalog.getId() == null) {
            entity = createEntity(d, catalog);
            em.persist(entity);
        } else {
            entity = mergeIntoExisting(d, catalog);
        }
        return toDomain(d, entity);
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> E createEntity(
            CatalogTypeDescriptor<E, TE> d, Catalog catalog) {
        E entity = newInstance(d.entityClass());
        entity.setWeight(catalog.getWeight());
        CatalogExtraFieldsAccessor.write(entity, d.extraFields(), catalog.getExtraFields());
        for (CatalogTranslation t : catalog.getTranslations()) {
            TE row = newInstance(d.translationEntityClass());
            row.setLocale(t.locale());
            row.setName(t.name());
            row.setDescription(t.description());
            row.linkParent(entity);
            entity.getTranslations().add(row);
        }
        return entity;
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> E mergeIntoExisting(
            CatalogTypeDescriptor<E, TE> d, Catalog catalog) {
        E existing = em.find(d.entityClass(), catalog.getId());
        if (existing == null) {
            throw new IllegalStateException(d.entityClass().getSimpleName() + " " + catalog.getId() + " not found");
        }

        existing.setWeight(catalog.getWeight());
        CatalogExtraFieldsAccessor.write(existing, d.extraFields(), catalog.getExtraFields());

        Map<String, TE> byLocale = existing.getTranslations().stream()
                .collect(Collectors.toMap(TE::getLocale, t -> t));
        Set<String> incomingLocales = catalog.getTranslations().stream()
                .map(CatalogTranslation::locale)
                .collect(Collectors.toSet());

        existing.getTranslations().removeIf(t -> !incomingLocales.contains(t.getLocale()));

        for (CatalogTranslation t : catalog.getTranslations()) {
            TE row = byLocale.get(t.locale());
            if (row != null) {
                row.setName(t.name());
                row.setDescription(t.description());
            } else {
                TE newRow = newInstance(d.translationEntityClass());
                newRow.setLocale(t.locale());
                newRow.setName(t.name());
                newRow.setDescription(t.description());
                newRow.linkParent(existing);
                existing.getTranslations().add(newRow);
            }
        }
        return existing;
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> void deleteByIdGeneric(
            CatalogTypeDescriptor<E, TE> d, Long id) {
        E entity = em.find(d.entityClass(), id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    private <E extends CatalogEntity<TE>, TE extends CatalogTranslationEntity<E>> Catalog toDomain(
            CatalogTypeDescriptor<E, TE> d, E entity) {
        return Catalog.builder()
                .id(entity.getId())
                .type(d.type())
                .translations(entity.getTranslations().stream()
                        .map(t -> new CatalogTranslation(t.getLocale(), t.getName(), t.getDescription()))
                        .toList())
                .weight(entity.getWeight() != null ? entity.getWeight() : 0)
                .extraFields(CatalogExtraFieldsAccessor.read(entity, d.extraFields()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
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
