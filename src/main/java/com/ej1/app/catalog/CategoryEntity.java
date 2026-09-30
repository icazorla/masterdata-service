package com.ej1.app.catalog;

import com.ej1.app.common.catalog.infrastructure.persistence.CatalogBaseEntity;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryEntity extends CatalogBaseEntity implements CatalogEntity<CategoryTranslationEntity> {

    /**
     * {@code EAGER}: datos maestros, un puñado de categorías con un par de locales
     * cada una, así que los selects adicionales son baratos y esto evita el
     * riesgo de {@code LazyInitializationException} de devolver un
     * {@code Catalog} desconectado cuyas traducciones nunca se cargaron.
     *
     * <p>{@code save()} siempre reemplaza esta lista por completo (ver
     * {@code CategoryEntityMapper#toEntity}) en lugar de parchear filas
     * individuales — con {@code orphanRemoval = true} eso elimina de forma
     * fiable las traducciones que ya no están presentes e inserta las nuevas,
     * al coste de que se asignen nuevos ids en cada actualización. Aquí no es
     * un problema: los ids de traducción nunca se exponen fuera de este
     * adaptador.
     */
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CategoryTranslationEntity> translations = new ArrayList<>();
}
