package io.masterdata.common.catalog.interfaces.rest.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRequestTest {

    @Test
    @DisplayName("Los atributos de solo lectura de la respuesta se ignoran; el resto llega como campo extra")
    void ignoresReadOnlyResponseFields() {
        CatalogRequest request = JsonMapper.builder().build().readValue("""
                {"id": 1, "type": "province", "createdAt": "2026-01-01T00:00:00", "updatedAt": "2026-01-01T00:00:00",
                 "ineCode": 41, "inecode": 4,
                 "translations": {"en": {"name": "Seville"}}}
                """, CatalogRequest.class);

        assertThat(request.getExtraFields()).containsOnlyKeys("ineCode", "inecode");
    }
}
