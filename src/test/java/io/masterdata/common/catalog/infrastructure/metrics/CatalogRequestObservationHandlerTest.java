package io.masterdata.common.catalog.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:convention-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform="
})
@AutoConfigureMockMvc
class CatalogRequestObservationHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    @Test
    @DisplayName("una petición a un catálogo alimenta los temporizadores con percentiles y el contador por método y estado")
    void recordsCatalogRequests() throws Exception {
        mockMvc.perform(get("/api/catalogs/province"));

        assertThat(registry.find("catalog.requests").tag("catalog", "province").timer().count()).isPositive();
        assertThat(registry.find("catalog.requests.all").timer().count()).isPositive();
        assertThat(registry.find("catalog.requests.by").tag("method", "GET").tag("status", "200").counter().count()).isPositive();
        // p95 y p99 publicados como gauges `catalog.requests.percentile` (etiqueta phi)
        assertThat(registry.find("catalog.requests.percentile").tag("catalog", "province").tag("phi", "0.95").gauge()).isNotNull();
    }

    @Test
    @DisplayName("las peticiones fuera de /{type} (health...) no cuentan")
    void ignoresOtherRequests() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(registry.find("catalog.requests").tag("catalog", "none").timer()).isNull();
    }
}
