package com.ej1.app.catalog;

import com.ej1.app.common.catalog.infrastructure.persistence.CatalogBaseEntity;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
@Table(name = "brands")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandEntity extends CatalogBaseEntity implements CatalogEntity<BrandTranslationEntity> {

    /**
     * Campo adicional propio de {@code brand} (ver {@code app.catalog.types.brand.extra-fields}
     * en {@code application.yml}) — {@code category} no tiene columna equivalente. Leído/escrito
     * genéricamente por {@link com.ej1.app.common.catalog.infrastructure.persistence.CatalogExtraFieldsAccessor}
     * a través de este getter/setter de JavaBean, igual que cualquier otro campo declarado en
     * {@code extra-fields}; esta clase no sabe nada de ese mecanismo, solo expone la columna.
     */
    @Column
    private String website;

    /**
     * {@code EAGER}: datos maestros, un puñado de marcas con un par de locales
     * cada una, así que los selects adicionales son baratos y esto evita el
     * riesgo de {@code LazyInitializationException} de devolver un
     * {@code Catalog} desconectado cuyas traducciones nunca se cargaron.
     *
     * <p>{@code save()} siempre reemplaza esta lista por completo (ver
     * {@code BrandEntityMapper#toEntity}) en lugar de parchear filas
     * individuales — con {@code orphanRemoval = true} eso elimina de forma
     * fiable las traducciones que ya no están presentes e inserta las nuevas,
     * al coste de que se asignen nuevos ids en cada actualización. Aquí no es
     * un problema: los ids de traducción nunca se exponen fuera de este
     * adaptador.
     */
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<BrandTranslationEntity> translations = new ArrayList<>();
}
