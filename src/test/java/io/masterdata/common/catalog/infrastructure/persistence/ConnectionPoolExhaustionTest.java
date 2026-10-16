package io.masterdata.common.catalog.infrastructure.persistence;

import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.CannotCreateTransactionException;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Con el pool agotado, una petición falla en {@code connection-timeout} con
 * {@link CannotCreateTransactionException} — la que {@code ApiExceptionHandler} traduce a 503 —
 * en vez de esperar indefinidamente. Pool de una sola conexión, retenida por el propio test.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pool-exhaustion;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=250"
})
class ConnectionPoolExhaustionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CatalogQueryService queryService;

    @Test
    @DisplayName("Sin conexiones libres, la lectura falla con CannotCreateTransactionException tras el connection-timeout")
    void exhaustedPoolFailsFast() throws Exception {
        try (Connection ignored = dataSource.getConnection()) {
            assertThatThrownBy(() -> queryService.findById(TestCatalogTypes.REGION, 1L))
                    .isInstanceOf(CannotCreateTransactionException.class);
        }
    }
}
