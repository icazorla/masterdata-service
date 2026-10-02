package com.ej1.app.common.catalog.interfaces.rest.query;

import com.ej1.app.common.catalog.domain.CatalogField;
import com.ej1.app.common.catalog.domain.CatalogTypeDefinition;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogPersistenceConfig;
import com.ej1.app.common.catalog.infrastructure.rest.CatalogRestMapperAdapter;
import com.ej1.app.common.rest.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogTypeController.class)
@Import({ApiExceptionHandler.class, CatalogRestMapperAdapter.class, CatalogPersistenceConfig.class, CatalogI18nConfig.class})
class CatalogTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService service;

    private static final CatalogField NAME = new CatalogField("name", "string", true, false, true, true, false);

    private void givenDefinitions() {
        when(service.findTypeDefinitions()).thenReturn(List.of(
                new CatalogTypeDefinition(TestCatalogTypes.BRAND, List.of(NAME, CatalogField.extra("website", "string"))),
                new CatalogTypeDefinition(TestCatalogTypes.CATEGORY, List.of(NAME))));
    }

    @Test
    void getTypes_returnsEachTypeWithItsFields() throws Exception {
        givenDefinitions();

        mockMvc.perform(get("/api/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("brand"))
                .andExpect(jsonPath("$[0].name").value("Brand"))
                .andExpect(jsonPath("$[0].fields.length()").value(2))
                .andExpect(jsonPath("$[0].fields[0].name").value("name"))
                .andExpect(jsonPath("$[0].fields[0].required").value(true))
                .andExpect(jsonPath("$[0].fields[0].translatable").value(true))
                .andExpect(jsonPath("$[0].fields[1].name").value("website"))
                .andExpect(jsonPath("$[0].fields[1].dataType").value("string"))
                .andExpect(jsonPath("$[0].fields[1].extra").value(true))
                .andExpect(jsonPath("$[1].type").value("category"))
                .andExpect(jsonPath("$[1].fields.length()").value(1));
    }

    @Test
    void getTypes_translatesTypeNameByAcceptLanguage() throws Exception {
        givenDefinitions();

        mockMvc.perform(get("/api/catalogs").header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Marca"))
                .andExpect(jsonPath("$[1].name").value("Categoría"));
    }
}
