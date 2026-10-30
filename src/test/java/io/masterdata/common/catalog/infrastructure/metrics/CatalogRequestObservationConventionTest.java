package io.masterdata.common.catalog.infrastructure.metrics;

import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

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
class CatalogRequestObservationConventionTest {

    private final CatalogRequestObservationConvention convention = new CatalogRequestObservationConvention();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    private String catalogTag(Map<String, String> pathVariables) {
        var request = new MockHttpServletRequest();
        if (pathVariables != null) {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
        }
        var context = new ServerRequestObservationContext(request, new MockHttpServletResponse());
        return convention.getLowCardinalityKeyValues(context).stream()
                .filter(kv -> kv.getKey().equals("catalog"))
                .map(kv -> kv.getValue())
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("el tipo configurado de la ruta, sin distinguir mayúsculas")
    void tagsConfiguredType() {
        assertThat(TestCatalogTypes.PROVINCE).isNotNull();
        assertThat(catalogTag(Map.of("type", "Province"))).isEqualTo("province");
    }

    @Test
    @DisplayName("none si no hay {type} o no es un tipo configurado (evita series ilimitadas)")
    void tagsNoneOtherwise() {
        assertThat(catalogTag(null)).isEqualTo("none");
        assertThat(catalogTag(Map.of("type", "../etc/passwd"))).isEqualTo("none");
    }

    @Test
    @DisplayName("una petición real a /api/catalogs/province queda registrada con catalog=province")
    void recordsRealRequests() throws Exception {
        mockMvc.perform(get("/api/catalogs/province"));

        assertThat(registry.find("http.server.requests").tag("catalog", "province").timer()).isNotNull();
    }
}
