package io.masterdata.common.catalog.infrastructure.cache;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.port.CatalogTranslationCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;
import io.masterdata.common.catalog.domain.service.CatalogCommandService;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.catalog.infrastructure.persistence.CatalogMapperAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code enabled} global y por tipo: con la caché global desactivada, un tipo que no indica
 * {@code enabled} la hereda desactivada ({@code province}) y uno que la reactiva
 * ({@code region}) sigue cacheando. Las cachés desactivadas no se crean y {@link CatalogCacheResolver}
 * les da un {@code NoOpCache}, así que lecturas y comandos funcionan igual, sin cachear.
 */
@SpringJUnitConfig({CatalogCacheConfig.class, CatalogQueryService.class, CatalogCommandService.class,
        CatalogMapperAdapter.class, CatalogCacheTest.TxConfig.class})
@TestPropertySource(properties = {
        "app.catalog.cache.enabled=false",
        "app.catalog.types.region.cache.enabled=true",
        "app.catalog.types.province.names.en=Province"
})
class CatalogCacheDisabledTest {

    @Autowired
    private CatalogQueryService queryService;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private CatalogRepository repository;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        when(repository.findById(any(), any())).thenAnswer(inv -> Optional.of(Catalog.builder().id(inv.getArgument(1))
                .type(inv.getArgument(0)).translations(List.of(new CatalogTranslation("en", "X", null))).build()));
        when(repository.extraFieldTypes(any())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Solo se crean las cachés activas: las del tipo que reactiva la caché")
    void onlyEnabledCachesExist() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("catalogById:region", "catalogLists:region");
    }

    @Test
    @DisplayName("Un tipo que hereda enabled=false no cachea, y sus comandos no fallan")
    void inheritedDisabledType_isNotCached() {
        queryService.findById(TestCatalogTypes.PROVINCE, 1L);
        commandService.update(TestCatalogTypes.PROVINCE, 1L, new UpdateCatalogCommand(0, true, Map.of(),
                List.of(new CatalogTranslationCommand("en", "Y", null))));
        queryService.findById(TestCatalogTypes.PROVINCE, 1L);

        verify(repository, times(3)).findById(TestCatalogTypes.PROVINCE, 1L);
    }

    @Test
    @DisplayName("Un tipo con enabled=true propio cachea aunque la global esté desactivada")
    void typeEnabledOverridesGlobal() {
        queryService.findById(TestCatalogTypes.REGION, 1L);
        queryService.findById(TestCatalogTypes.REGION, 1L);

        verify(repository, times(1)).findById(TestCatalogTypes.REGION, 1L);
    }

    @Test
    @DisplayName("La caché global de tipos sigue la configuración global (desactivada)")
    void typesCacheFollowsGlobalSetting() {
        queryService.findTypeDefinitions();
        queryService.findTypeDefinitions();

        verify(repository, times(2)).extraFieldTypes(TestCatalogTypes.REGION);
    }
}
