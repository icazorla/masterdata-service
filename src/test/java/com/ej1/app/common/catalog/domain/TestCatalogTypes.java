package com.ej1.app.common.catalog.domain;

import java.util.List;

/**
 * Tipos de catálogo de conveniencia para tests. {@link CatalogType} toma sus valores de
 * {@code app.catalog.types} vía {@code CatalogPersistenceConfig}, pero los tests unitarios
 * (sin contexto Spring) y los inicializadores estáticos / {@code @MethodSource} (que se
 * evalúan antes de levantar el contexto) necesitan los tipos ya registrados — esta clase
 * los registra con los mismos códigos que {@code application.yml}.
 */
public final class TestCatalogTypes {

    static {
        CatalogType.configure(List.of("brand", "category"));
    }

    public static final CatalogType BRAND = CatalogType.of("brand");
    public static final CatalogType CATEGORY = CatalogType.of("category");

    private TestCatalogTypes() {
    }
}
