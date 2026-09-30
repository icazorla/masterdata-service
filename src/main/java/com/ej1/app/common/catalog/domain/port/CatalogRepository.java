package com.ej1.app.common.catalog.domain.port;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de salida para la persistencia de catálogos. El dominio define lo que
 * necesita del mundo exterior; un único adaptador de infraestructura implementa
 * este contrato para todos los {@link CatalogType} (ver
 * {@code CatalogRepositoryAdapter}), así que cada método recibe {@code type}
 * explícitamente en lugar de existir un bean por tipo.
 */
public interface CatalogRepository {

    Optional<Catalog> findById(CatalogType type, Long id);

    boolean existsById(CatalogType type, Long id);

    /** Si alguna entrada de catálogo de este tipo ya tiene {@code name} (sin distinguir mayúsculas/minúsculas) para {@code locale}. */
    boolean existsByLocaleAndNameIgnoreCase(CatalogType type, String locale, String name);

    /** Igual que {@link #existsByLocaleAndNameIgnoreCase}, ignorando la entrada que se está actualizando ({@code excludeId}). */
    boolean existsByLocaleAndNameIgnoreCaseAndIdNot(CatalogType type, String locale, String name, Long excludeId);

    /**
     * Nombres de los campos adicionales propios de {@code type} (ver
     * {@code app.catalog.types.<type>.extra-fields} en {@code application.yml}, p. ej.
     * {@code "website"} solo para {@code brand}) — además de {@code name}/{@code description}/
     * {@code weight}, también son valores válidos de {@code sortBy} para {@link #findAll}.
     * Expuesto aquí (y no como una constante fija en el dominio) porque qué campos extra
     * tiene cada tipo es conocimiento de configuración/persistencia, no de negocio.
     */
    Set<String> sortableExtraFields(CatalogType type);

    /**
     * Devuelve las entradas de este tipo que cumplen {@code criteria}, ordenadas por
     * {@code sortBy} ({@code "name"}/{@code "description"}) en la dirección indicada
     * ({@code "ASC"}/{@code "DESC"}, sin distinguir mayúsculas). Datos maestros — sin
     * paginación: se espera que la tabla se mantenga pequeña (se usa para poblar
     * filtros/selects).
     *
     * <p>El filtro y el orden se evalúan en base de datos sobre la traducción resuelta
     * para {@code locale} (código de idioma, p. ej. {@code "es"}), recurriendo a la
     * traducción del idioma por defecto cuando este catálogo no tiene ninguna para
     * {@code locale} — el mismo fallback que aplica {@code Catalog#resolveTranslation}
     * para decidir qué name/description mostrar a este cliente, así que el orden/filtro
     * siempre coincide con lo que el cliente ve en la respuesta.
     */
    List<Catalog> findAll(CatalogType type, String locale, CatalogSearchCriteria criteria, String sortBy, String sortDirection);

    /** {@code catalog.getType()} determina en qué tabla se persiste esto — no hace falta un parámetro {@code type} aparte. */
    Catalog save(Catalog catalog);

    void deleteById(CatalogType type, Long id);
}
