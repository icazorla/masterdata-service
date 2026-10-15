package io.masterdata.common.catalog.domain.exception;

import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.domain.exception.StaleResourceException;

/**
 * La entrada ya no está en la versión sobre la que el cliente hizo sus cambios: otra persona la
 * modificó entre medias. Una sola clase para todos los tipos — ver {@link CatalogNotFoundException}.
 */
public class CatalogVersionConflictException extends StaleResourceException {
    public CatalogVersionConflictException(CatalogType type, Long id, Long expectedVersion, Long currentVersion) {
        super(type.label() + " " + id + " is at version " + currentVersion + ", not " + expectedVersion,
                "error.catalog.versionConflict", type, id, expectedVersion, currentVersion);
    }
}
