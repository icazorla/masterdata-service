package com.ej1.app.common.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Base autocontenida para las entidades JPA <em>padre</em> de un catálogo
 * ({@code BrandEntity}, {@code CategoryEntity}, ...): {@code id} autogenerado,
 * {@code created_at}/{@code updated_at} rellenados por Hibernate, y {@code weight}
 * (peso de ordenación manual, exclusivo de catálogo) — los cuatro campos comunes a
 * todo catálogo, unificados aquí en lugar de repetirse por tipo, igual que
 * {@code CatalogTranslationBaseEntity} hace para las filas de traducción.
 *
 * <p>Declarada de forma independiente de {@code BaseEntity}/{@code AuditableEntity}
 * (las bases genéricas que sí comparte {@code ProductEntity}) a propósito: la
 * jerarquía de catálogo no depende de esas clases, y {@code weight} (entero, peso de
 * ordenación) no puede vivir en una base tan amplia sin colisionar con el propio
 * {@code weight} de {@code ProductEntity} (peso físico, {@code BigDecimal}) — un
 * concepto completamente distinto.
 *
 * <p>Sin {@code @Builder}/{@code @SuperBuilder}: {@code BrandEntity}/{@code CategoryEntity}
 * usan {@code @Builder} (no {@code @SuperBuilder}), que solo expone los campos declarados
 * en la propia clase — estos cuatro pasan a fijarse vía sus setters tras construir, no vía
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
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * {@code nullable = false} + {@code columnDefinition}: la columna en sí no admite
     * {@code NULL} y tiene {@code DEFAULT 0} en base de datos — no solo el inicializador
     * de campo Java de arriba, que únicamente cubre la ruta {@code newInstance} + setters
     * de {@link CatalogRepositoryAdapter}. Un {@code INSERT} que no fije {@code weight}
     * por cualquier otro camino recibe igualmente {@code 0}, nunca {@code NULL}.
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer weight = 0;
}
