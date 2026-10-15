package io.masterdata.common.catalog.interfaces.rest.admin;

import io.masterdata.common.catalog.domain.CatalogField;
import io.masterdata.common.catalog.domain.CatalogTypeDefinition;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.infrastructure.i18n.CatalogI18nConfig;
import io.masterdata.common.catalog.interfaces.rest.mapper.CatalogRestMapper;
import io.masterdata.common.rest.exception.ApiExceptionHandler;
import io.masterdata.common.rest.exception.SecurityErrorHandler;
import io.masterdata.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogTypeController.class)
@Import({ApiExceptionHandler.class, CatalogRestMapper.class, CatalogI18nConfig.class,
        SecurityConfig.class, SecurityErrorHandler.class})
@WithMockUser(roles = SecurityConfig.ADMIN)
class CatalogTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService service;

    private static final CatalogField NAME = new CatalogField("name", "string", true, false, true, true, false);

    private void givenDefinitions() {
        when(service.findTypeDefinitions()).thenReturn(List.of(
                new CatalogTypeDefinition(TestCatalogTypes.REGION, List.of(NAME, CatalogField.extra("ineCode", "integer"))),
                new CatalogTypeDefinition(TestCatalogTypes.PROVINCE, List.of(NAME))));
    }

    @Test
    void getTypes_returnsEachTypeWithItsFields() throws Exception {
        givenDefinitions();

        mockMvc.perform(get("/admin/api/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("region"))
                .andExpect(jsonPath("$[0].name").value("Autonomous community"))
                .andExpect(jsonPath("$[0].fields.length()").value(2))
                .andExpect(jsonPath("$[0].fields[0].name").value("name"))
                .andExpect(jsonPath("$[0].fields[0].required").value(true))
                .andExpect(jsonPath("$[0].fields[0].translatable").value(true))
                .andExpect(jsonPath("$[0].fields[1].name").value("ineCode"))
                .andExpect(jsonPath("$[0].fields[1].dataType").value("integer"))
                .andExpect(jsonPath("$[0].fields[1].extra").value(true))
                .andExpect(jsonPath("$[0].fields[0].filterOperators").isEmpty())
                .andExpect(jsonPath("$[0].fields[1].filterOperators[0]").value("eq"))
                .andExpect(jsonPath("$[1].type").value("province"))
                .andExpect(jsonPath("$[1].fields.length()").value(1));
    }

    @Test
    void getTypes_translatesTypeNameByAcceptLanguage() throws Exception {
        givenDefinitions();

        mockMvc.perform(get("/admin/api/catalogs").header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Comunidad autónoma"))
                .andExpect(jsonPath("$[1].name").value("Provincia"));
    }

    @Test
    @WithAnonymousUser
    void security_returns401WithoutCredentials() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "USER")
    void security_returns403WithoutAdminRole() throws Exception {
        mockMvc.perform(get("/admin/api/catalogs"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("La antigua ruta pública /api/catalogs ya no existe: 404")
    void oldQueryPath_returns404() throws Exception {
        mockMvc.perform(get("/api/catalogs"))
                .andExpect(status().isNotFound());
    }
}
