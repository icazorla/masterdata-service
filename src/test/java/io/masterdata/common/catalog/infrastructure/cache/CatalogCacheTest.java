package io.masterdata.common.catalog.infrastructure.cache;

import io.masterdata.common.catalog.domain.Catalog;
import io.masterdata.common.catalog.domain.CatalogTranslation;
import io.masterdata.common.catalog.domain.CatalogType;
import io.masterdata.common.catalog.domain.TestCatalogTypes;
import io.masterdata.common.catalog.domain.TestCriteria;
import io.masterdata.common.catalog.domain.exception.CatalogNotFoundException;
import io.masterdata.common.catalog.domain.port.CatalogRepository;
import io.masterdata.common.catalog.domain.port.CatalogSearchCriteria;
import io.masterdata.common.catalog.domain.port.CatalogFilter;
import io.masterdata.common.catalog.domain.port.CatalogTranslationCommand;
import io.masterdata.common.catalog.domain.port.CreateCatalogCommand;
import io.masterdata.common.catalog.domain.port.UpdateCatalogCommand;
import io.masterdata.common.catalog.domain.service.CatalogCacheNames;
import io.masterdata.common.catalog.domain.service.CatalogCommandService;
import io.masterdata.common.catalog.domain.service.CatalogQueryService;
import io.masterdata.common.domain.Paging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caché de catálogos con los proxies reales de Spring ({@code @Cacheable}/{@code @CacheEvict} +
 * {@code @Transactional} + {@link CatalogCacheResolver}) sobre {@link CatalogQueryService}/
 * {@link CatalogCommandService}, con el {@link CatalogRepository} simulado: cuántas veces se llega
 * al repositorio dice si la lectura salió de caché. {@link NoOpTransactionManager} da transacciones
 * reales (sincronización incluida) sin base de datos, para comprobar que la invalidación espera al
 * commit. Es independiente de la librería: lo que cada proveedor hace con la configuración se cubre
 * en sus propios tests (p. ej. {@code CaffeineCatalogCacheProviderTest}), la herencia de la
 * configuración en {@link CatalogCacheSettingsTest} y la desactivación en {@link CatalogCacheDisabledTest}.
 */
@SpringJUnitConfig({CatalogCacheConfig.class, CatalogQueryService.class, CatalogCommandService.class, CatalogCacheTest.TxConfig.class})
@TestPropertySource(properties = {
        "app.catalog.cache.maximum-size=100",
        "app.catalog.types.region.cache.maximum-size=50",
        "app.catalog.types.region.cache.expire-after-write=1h",
        "app.catalog.types.province.names.en=Province"  // sin cache propia: hereda la global
})
class CatalogCacheTest {

    private static final CatalogType REGION = TestCatalogTypes.REGION;
    private static final CatalogType PROVINCE = TestCatalogTypes.PROVINCE;
    private static final CatalogSearchCriteria NO_FILTER = TestCriteria.of(null, null, null);

    @Autowired
    private CatalogQueryService queryService;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TransactionTemplate tx;

    @MockitoBean
    private CatalogRepository repository;

    private final Catalog logitech = Catalog.builder().id(1L).type(REGION)
            .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();

    private final Catalog peripherals = Catalog.builder().id(1L).type(PROVINCE)
            .translations(List.of(new CatalogTranslation("en", "Peripherals", "desc"))).build();

    private final UpdateCatalogCommand rename = new UpdateCatalogCommand(0, true, Map.of(),
            List.of(new CatalogTranslationCommand("en", "Logitech Pro", "desc")));

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        when(repository.findById(REGION, 1L)).thenReturn(Optional.of(logitech));
        when(repository.findById(PROVINCE, 1L)).thenReturn(Optional.of(peripherals));
        when(repository.findAll(any(), any(), any(), any(), any())).thenReturn(List.of(logitech));
        when(repository.sortableExtraFields(any())).thenReturn(Set.of());
        when(repository.extraFieldTypes(any())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Cada tipo tiene sus propias cachés, más la global de tipos")
    void cachesArePerType() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(
                "catalogTypes", "catalogById:region", "catalogLists:region", "catalogById:province", "catalogLists:province");
    }

    @Test
    void findById_isCached() {
        assertThat(queryService.findById(REGION, 1L)).isSameAs(logitech);
        assertThat(queryService.findById(REGION, 1L)).isSameAs(logitech);

        verify(repository, times(1)).findById(REGION, 1L);
        assertThat(cacheManager.getCache("catalogById:region").get(1L)).isNotNull();
    }

    @Test
    @DisplayName("El mismo id en dos tipos son entradas distintas, cada una en la caché de su tipo")
    void findById_sameIdInTwoTypesDoesNotCollide() {
        assertThat(queryService.findById(REGION, 1L)).isSameAs(logitech);
        assertThat(queryService.findById(PROVINCE, 1L)).isSameAs(peripherals);
        assertThat(queryService.findById(PROVINCE, 1L)).isSameAs(peripherals);

        verify(repository, times(1)).findById(REGION, 1L);
        verify(repository, times(1)).findById(PROVINCE, 1L);
    }

    @Test
    @DisplayName("findById no se cachea cuando no existe: el 404 se vuelve a comprobar en cada petición")
    void findById_notFoundIsNotCached() {
        when(repository.findById(REGION, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.findById(REGION, 2L)).isInstanceOf(CatalogNotFoundException.class);
        assertThatThrownBy(() -> queryService.findById(REGION, 2L)).isInstanceOf(CatalogNotFoundException.class);

        verify(repository, times(2)).findById(REGION, 2L);
    }

    @Test
    @DisplayName("findAll sin filtro se cachea por (type, locale, criteria, sortBy, sortDirection)")
    void findAll_unfilteredIsCachedPerKey() {
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(REGION, "es", NO_FILTER, "name", "ASC");

        verify(repository, times(1)).findAll(REGION, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findAll(REGION, "es", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("findAll con filtro de texto no se cachea")
    void findAll_filteredIsNotCached() {
        var criteria = TestCriteria.of("logi", null, null);
        queryService.findAll(REGION, "en", criteria, "name", "ASC");
        queryService.findAll(REGION, "en", criteria, "name", "ASC");

        verify(repository, times(2)).findAll(REGION, "en", criteria, "name", "ASC");
    }

    @Test
    @DisplayName("findAll con filtro de campo adicional no se cachea")
    void findAll_filteredByExtraFieldIsNotCached() {
        var criteria = new CatalogSearchCriteria(List.of(new CatalogFilter("ineCode", "1")));
        when(repository.extraFieldTypes(REGION)).thenReturn(Map.of("ineCode", "integer"));
        queryService.findAll(REGION, "en", criteria, "name", "ASC");
        queryService.findAll(REGION, "en", criteria, "name", "ASC");

        verify(repository, times(2)).findAll(REGION, "en", criteria, "name", "ASC");
    }

    @Test
    @DisplayName("findAll filtrando solo por enabled sí se cachea (la llamada de la API de consulta sin filtro)")
    void findAll_filteredOnlyByEnabledIsCached() {
        var criteria = TestCriteria.of(null, null, true);
        queryService.findAll(REGION, "en", criteria, "name", "ASC");
        queryService.findAll(REGION, "en", criteria, "name", "ASC");

        verify(repository, times(1)).findAll(REGION, "en", criteria, "name", "ASC");
    }

    @Test
    void findPage_isNotCached() {
        var paging = new Paging(0, 20, "name", "ASC");
        queryService.findPage(REGION, "en", NO_FILTER, paging);
        queryService.findPage(REGION, "en", NO_FILTER, paging);

        verify(repository, times(2)).findPage(REGION, "en", NO_FILTER, paging);
    }

    @Test
    void findTypeDefinitions_isCached() {
        queryService.findTypeDefinitions();
        queryService.findTypeDefinitions();

        verify(repository, times(1)).extraFieldTypes(REGION);
    }

    @Test
    @DisplayName("create vacía los listados de su tipo, no los de los demás")
    void create_evictsListsOfItsTypeOnly() {
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");

        commandService.create(new CreateCatalogCommand(REGION, 0, true, Map.of(),
                List.of(new CatalogTranslationCommand("en", "Razer", "desc"))));
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");

        verify(repository, times(2)).findAll(REGION, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("update invalida la entrada por id y los listados de su tipo, no las cachés de los demás")
    void update_evictsByIdAndListsOfItsTypeOnly() {
        queryService.findById(REGION, 1L);
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findById(PROVINCE, 1L);
        queryService.findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");

        commandService.update(REGION, 1L, rename);  // llama a repository.findById(REGION, 1) por su cuenta
        queryService.findById(REGION, 1L);
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");
        queryService.findById(PROVINCE, 1L);
        queryService.findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");

        verify(repository, times(3)).findById(REGION, 1L);
        verify(repository, times(2)).findAll(REGION, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findById(PROVINCE, 1L);
        verify(repository, times(1)).findAll(PROVINCE, "en", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("delete (lógico) invalida la entrada por id y los listados")
    void delete_evictsByIdAndLists() {
        queryService.findById(REGION, 1L);                       // repo: 1
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");

        commandService.delete(REGION, 1L);                        // repo: 2 (el propio delete lee la entrada)
        queryService.findById(REGION, 1L);                       // repo: 3 (invalidada)
        queryService.findAll(REGION, "en", NO_FILTER, "name", "ASC");

        verify(repository, times(3)).findById(REGION, 1L);
        verify(repository, times(2)).findAll(REGION, "en", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("Un comando que falla (404) no invalida nada")
    void failedCommand_doesNotEvict() {
        queryService.findById(REGION, 1L);
        when(repository.findById(REGION, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.delete(REGION, 9L)).isInstanceOf(CatalogNotFoundException.class);
        queryService.findById(REGION, 1L);

        verify(repository, times(1)).findById(REGION, 1L);
    }

    @Test
    @DisplayName("Dentro de una transacción la invalidación espera al commit")
    void eviction_isDeferredUntilCommit() {
        queryService.findById(REGION, 1L);                       // repo: 1

        tx.executeWithoutResult(status -> {
            commandService.update(REGION, 1L, rename);           // repo: 2 (lectura propia del update)
            queryService.findById(REGION, 1L);                   // aún en caché → repo: 2
            verify(repository, times(2)).findById(REGION, 1L);
        });

        queryService.findById(REGION, 1L);                       // tras el commit → repo: 3
        verify(repository, times(3)).findById(REGION, 1L);
    }

    @Test
    @DisplayName("Si la transacción hace rollback, la caché no se invalida")
    void rollback_keepsCache() {
        queryService.findById(REGION, 1L);                       // repo: 1

        tx.executeWithoutResult(status -> {
            commandService.update(REGION, 1L, rename);           // repo: 2
            status.setRollbackOnly();
        });

        queryService.findById(REGION, 1L);                       // sigue en caché → repo: 2
        verify(repository, times(2)).findById(REGION, 1L);
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    /**
     * Gestor de transacciones sin recurso: begin/commit/rollback no hacen nada, pero
     * {@link AbstractPlatformTransactionManager} sí gestiona la sincronización (los callbacks
     * {@code afterCommit} de los que depende {@code TransactionAwareCacheManagerProxy}) y la
     * propagación — un {@code @Transactional} anidado se une a la transacción en curso.
     */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return (Boolean) transaction;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        }
    }
}
