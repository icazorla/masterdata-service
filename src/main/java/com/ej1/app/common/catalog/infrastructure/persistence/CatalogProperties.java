package com.ej1.app.common.catalog.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula {@code app.catalog.types.<type>} desde {@code application.yml} — los nombres
 * de clase completamente cualificados del par de entidades {@code @Entity} que respalda
 * cada {@link com.ej1.app.common.catalog.domain.CatalogType}, más los nombres de sus
 * campos adicionales propios del tipo ({@code extraFields}, p. ej. {@code website} solo
 * en {@code brand}). {@link CatalogPersistenceConfig} resuelve los nombres de clase de
 * cada entrada mediante reflexión para construir el registro real de
 * {@code CatalogTypeDescriptor}, de modo que añadir un tipo de catálogo — o un campo
 * adicional a uno existente — es un cambio de configuración (más, en el caso de un tipo
 * nuevo, sus dos clases {@code @Entity}; en el caso de un campo adicional, la columna
 * {@code @Column} correspondiente en la entidad ya existente), nunca una nueva clase
 * Java de registro. {@code extraFields} solo lleva los <em>nombres</em> — qué getter/
 * setter de JavaBean invocar para cada uno lo resuelve en tiempo de ejecución
 * {@link CatalogExtraFieldsAccessor} contra la entidad concreta.
 */
@ConfigurationProperties(prefix = "app.catalog")
public class CatalogProperties {

    private Map<String, TypeEntry> types = new LinkedHashMap<>();

    public Map<String, TypeEntry> getTypes() {
        return types;
    }

    public void setTypes(Map<String, TypeEntry> types) {
        this.types = types;
    }

    public static class TypeEntry {

        private String entityClass;
        private String translationEntityClass;
        private List<String> extraFields = new ArrayList<>();

        public String getEntityClass() {
            return entityClass;
        }

        public void setEntityClass(String entityClass) {
            this.entityClass = entityClass;
        }

        public String getTranslationEntityClass() {
            return translationEntityClass;
        }

        public void setTranslationEntityClass(String translationEntityClass) {
            this.translationEntityClass = translationEntityClass;
        }

        public List<String> getExtraFields() {
            return extraFields;
        }

        public void setExtraFields(List<String> extraFields) {
            this.extraFields = extraFields;
        }
    }
}
