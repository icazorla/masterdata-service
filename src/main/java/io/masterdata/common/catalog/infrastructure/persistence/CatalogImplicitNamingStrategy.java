package io.masterdata.common.catalog.infrastructure.persistence;

import org.hibernate.boot.model.naming.EntityNaming;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitCollectionTableNameSource;
import org.hibernate.boot.model.naming.ImplicitJoinColumnNameSource;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;

/**
 * Convención de nombres para las traducciones de catálogo, para que ninguna entidad tenga que
 * declarar su {@code @CollectionTable}: en cualquier subclase de {@link CatalogBaseEntity}, la
 * colección {@code translations} se guarda en {@code <tipo>_translations} con FK
 * {@code <tipo>_id}, donde {@code <tipo>} es el nombre de la clase sin el sufijo {@code Entity}
 * ({@code ProvinceEntity} → {@code Province}). {@code CamelCaseToUnderscoresNamingStrategy} (la
 * estrategia física por defecto de Spring) lo convierte después en {@code province_translations} /
 * {@code province_id}.
 *
 * <p>Extiende la estrategia implícita por defecto de Spring, así que cualquier otra entidad o
 * colección conserva exactamente los nombres que tenía. Registrada en {@code application.yml}
 * ({@code spring.jpa.hibernate.naming.implicit-strategy}).
 */
public class CatalogImplicitNamingStrategy extends SpringImplicitNamingStrategy {

    private static final String TRANSLATIONS = "translations";
    private static final String ENTITY_SUFFIX = "Entity";

    @Override
    public Identifier determineCollectionTableName(ImplicitCollectionTableNameSource source) {
        if (isCatalog(source.getOwningEntityNaming())
                && TRANSLATIONS.equals(source.getOwningAttributePath().getProperty())) {
            return toIdentifier(typeName(source.getOwningEntityNaming()) + "_" + TRANSLATIONS,
                    source.getBuildingContext());
        }
        return super.determineCollectionTableName(source);
    }

    /**
     * La FK de la tabla de una {@code @ElementCollection} no trae la ruta del atributo: Hibernate 7
     * la pide con {@code nature = ENTITY} (no {@code ELEMENT_COLLECTION}) y
     * {@code attributePath = null}, así que se filtra por entidad de catálogo + ruta nula —
     * {@code translations} es la única colección de un catálogo; un {@code @ManyToOne} propio
     * sí traería su ruta y conservaría el nombre por defecto.
     */
    @Override
    public Identifier determineJoinColumnName(ImplicitJoinColumnNameSource source) {
        if (source.getAttributePath() == null && isCatalog(source.getEntityNaming())) {
            return toIdentifier(typeName(source.getEntityNaming()) + "_" + source.getReferencedColumnName().getText(),
                    source.getBuildingContext());
        }
        return super.determineJoinColumnName(source);
    }

    private static boolean isCatalog(EntityNaming naming) {
        if (naming == null || naming.getClassName() == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName(naming.getClassName(), false,
                    Thread.currentThread().getContextClassLoader());
            return CatalogBaseEntity.class.isAssignableFrom(type);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** {@code io.masterdata.catalog.ProvinceEntity} → {@code Province}. */
    private static String typeName(EntityNaming naming) {
        String className = naming.getClassName();
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return simple.endsWith(ENTITY_SUFFIX)
                ? simple.substring(0, simple.length() - ENTITY_SUFFIX.length())
                : simple;
    }
}
