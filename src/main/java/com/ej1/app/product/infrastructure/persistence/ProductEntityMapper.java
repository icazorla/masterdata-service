package com.ej1.app.product.infrastructure.persistence;

import com.ej1.app.catalog.BrandEntity;
import com.ej1.app.catalog.BrandTranslationEntity;
import com.ej1.app.catalog.CategoryEntity;
import com.ej1.app.catalog.CategoryTranslationEntity;
import com.ej1.app.product.domain.Product;
import jakarta.persistence.EntityManager;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * Mapper local del adaptador entre la {@link ProductEntity} JPA y el {@link Product}
 * de dominio. Deliberadamente NO se expone a través de ningún puerto —
 * es un detalle de implementación del adaptador de persistencia.
 *
 * <p>La implementación la genera MapStruct en el mismo paquete
 * ({@code ProductEntityMapperImpl} en {@code target/generated-sources/}).
 *
 * <p>{@code id} se mapea desde el dominio para que las actualizaciones pasen por
 * {@code merge()} en lugar de {@code persist()} — mediante {@link #mapId}, no la
 * anotación {@code @Mapping}, porque {@code id} vive en
 * {@link com.ej1.app.common.infrastructure.persistence.BaseEntity}, fuera del builder
 * generado por Lombok (que solo cubre los campos declarados en {@link ProductEntity}
 * misma). {@code createdAt} / {@code updatedAt} viven igualmente fuera del builder, en
 * {@link com.ej1.app.common.infrastructure.persistence.AuditableEntity}, de modo que
 * {@code @CreationTimestamp} / {@code @UpdateTimestamp} de Hibernate siguen poblándolos
 * sin interferencia. {@code category}/{@code brand}
 * son asociaciones {@code @ManyToOne} en la entidad; la referencia real del dominio
 * es el id plano ({@code categoryId}/{@code brandId} — {@link Product} referencia esos
 * agregados solo por identidad), pero {@code toDomain} también copia el {@code name}
 * de la fila asociada en {@code categoryName}/{@code brandName}, una etiqueta de solo
 * lectura para mostrar. {@code categoryName}/{@code brandName} se resuelven desde
 * {@link CategoryEntity#getTranslations()}/{@link BrandEntity#getTranslations()} para
 * el locale por defecto de la aplicación ({@code en}) — la API de Product no tiene
 * concepto propio de locale, así que siempre muestra el mismo idioma sin importar el
 * {@code Accept-Language} del llamante, a diferencia de las respuestas propias de
 * {@code com.ej1.app.common.catalog.interfaces.rest.CatalogController}.
 * Eso solo funciona cuando quien llama cargó
 * {@code entity} con la asociación poblada (ver el {@code @EntityGraph} de
 * {@code ProductJpaRepository} / el fetch join de {@code ProductSpecifications}
 * para las lecturas) — {@code toEntity}, en el sentido contrario, nunca necesita más
 * que el id, resuelto mediante {@link EntityManager#getReference} (ver
 * {@link #categoryRef}/{@link #brandRef}).
 */
@Mapper(componentModel = "spring")
public interface ProductEntityMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category", qualifiedByName = "categoryDisplayName")
    @Mapping(target = "brandId", source = "brand.id")
    @Mapping(target = "brandName", source = "brand", qualifiedByName = "brandDisplayName")
    Product toDomain(ProductEntity entity);

    @Mapping(target = "category", source = "categoryId", qualifiedByName = "categoryRef")
    @Mapping(target = "brand", source = "brandId", qualifiedByName = "brandRef")
    ProductEntity toEntity(Product product, @Context EntityManager em);

    /** Ver el Javadoc de la clase: {@code id} vive fuera del builder generado, así que se copia aparte. */
    @AfterMapping
    default void mapId(Product product, @MappingTarget ProductEntity entity) {
        entity.setId(product.getId());
    }

    /**
     * Un proxy real de Hibernate que solo lleva el id, no un simple POJO detached — Hibernate
     * lee el id de él para poblar la columna FK al guardar sin necesidad de consulta, pero a
     * diferencia de un objeto vacío construido a mano, este proxy se inicializa perezosamente
     * (un SELECT) en el momento en que algo (p. ej. el mapeo {@code category.name} de
     * {@code toDomain}, invocado justo después de guardar mientras la transacción sigue abierta)
     * lee un campo que no sea el id. Eso es lo que hace que {@code categoryName}/{@code brandName}
     * vuelvan poblados inmediatamente tras un create/update en vez de null.
     */
    @Named("categoryRef")
    default CategoryEntity categoryRef(Long id, @Context EntityManager em) {
        return id == null ? null : em.getReference(CategoryEntity.class, id);
    }

    @Named("brandRef")
    default BrandEntity brandRef(Long id, @Context EntityManager em) {
        return id == null ? null : em.getReference(BrandEntity.class, id);
    }

    /** Ver el Javadoc de la clase: siempre el nombre del locale por defecto ({@code en}), sin importar el locale del llamante. */
    @Named("brandDisplayName")
    default String brandDisplayName(BrandEntity brand) {
        if (brand == null) {
            return null;
        }
        return brand.getTranslations().stream()
                .filter(t -> "en".equalsIgnoreCase(t.getLocale()))
                .findFirst()
                .map(BrandTranslationEntity::getName)
                .orElse(null);
    }

    /** Ver el Javadoc de la clase: siempre el nombre del locale por defecto ({@code en}), sin importar el locale del llamante. */
    @Named("categoryDisplayName")
    default String categoryDisplayName(CategoryEntity category) {
        if (category == null) {
            return null;
        }
        return category.getTranslations().stream()
                .filter(t -> "en".equalsIgnoreCase(t.getLocale()))
                .findFirst()
                .map(CategoryTranslationEntity::getName)
                .orElse(null);
    }
}
