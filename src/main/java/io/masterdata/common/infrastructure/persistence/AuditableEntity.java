package io.masterdata.common.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Base para las entidades JPA que, además del {@code id} de {@link BaseEntity}, llevan
 * las columnas estándar {@code created_at}/{@code updated_at}, rellenadas por Hibernate
 * ({@link CreationTimestamp}/{@link UpdateTimestamp}) en lugar de por el código de la aplicación.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity extends BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
