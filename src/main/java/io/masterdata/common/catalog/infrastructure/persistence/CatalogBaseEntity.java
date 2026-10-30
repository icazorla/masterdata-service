package io.masterdata.common.catalog.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Base autocontenida para las entidades JPA <em>padre</em> de un catálogo
 * ({@code RegionEntity}, {@code ProvinceEntity}, ...): {@code id} autogenerado,
 * {@code created_at}/{@code updated_at} rellenados por Hibernate, {@code weight}
 * (peso de ordenación manual, exclusivo de catálogo), {@code enabled} y {@code translations}
 * — los campos comunes a todo catálogo, unificados aquí en lugar de repetirse por tipo, igual
 * que {@link CatalogTranslationValue} hace para las filas de traducción.
 *
 * <p>Sin {@code @Builder}/{@code @SuperBuilder}: {@code RegionEntity}/{@code ProvinceEntity}
 * usan {@code @Builder} (no {@code @SuperBuilder}), que solo expone los campos declarados
 * en la propia clase — estos pasan a fijarse vía sus setters tras construir, no vía
 * el builder. {@link CatalogRepositoryAdapter} ya construye estas entidades así
 * ({@code newInstance} + setters), así que esto no cambia su comportamiento.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class CatalogBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Control optimista de concurrencia: Hibernate la incrementa en cada escritura y rechaza
     * guardar sobre una versión que otra transacción ya ha cambiado. La API la expone como
     * {@code version} y {@code ETag}, y la comprueba contra {@code If-Match}.
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer weight = 0;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean enabled = true;

    /**
     * {@code EAGER}: datos maestros, un puñado de entradas con un par de locales
     * cada una, así que los selects adicionales son baratos y esto evita el
     * riesgo de {@code LazyInitializationException} de devolver un
     * {@code Catalog} desconectado cuyas traducciones nunca se cargaron.
     *
     * <p>Colección de valores ({@link CatalogTranslationValue}, común a todos los catálogos)
     * en vez de una entidad de traducción por tipo. Lo único propio de cada tipo — la tabla
     * y la FK donde se guardan ({@code brand_translations}/{@code brand_id}, ...) — no se
     * declara aquí ni en la subclase: lo deriva {@link CatalogImplicitNamingStrategy} del
     * nombre de la clase. {@code (locale, name)} es único a través de todas las entradas
     * del tipo, no solo dentro de una.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(uniqueConstraints = @UniqueConstraint(columnNames = {"locale", "name"}))
    private List<CatalogTranslationValue> translations = new ArrayList<>();
}
