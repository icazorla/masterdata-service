package io.masterdata.common.catalog.domain;

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
        CatalogType.configure(List.of("region", "province"));
    }

    public static final CatalogType REGION = CatalogType.of("region");
    public static final CatalogType PROVINCE = CatalogType.of("province");

    private TestCatalogTypes() {
    }
}
