package com.ej1.app.common.catalog.infrastructure.cache;

import com.ej1.app.common.catalog.domain.Catalog;
import com.ej1.app.common.catalog.domain.CatalogTranslation;
import com.ej1.app.common.catalog.domain.CatalogType;
import com.ej1.app.common.catalog.domain.TestCatalogTypes;
import com.ej1.app.common.catalog.domain.exception.CatalogNotFoundException;
import com.ej1.app.common.catalog.domain.port.CatalogRepository;
import com.ej1.app.common.catalog.domain.port.CatalogSearchCriteria;
import com.ej1.app.common.catalog.domain.port.CatalogTranslationCommand;
import com.ej1.app.common.catalog.domain.port.CreateCatalogCommand;
import com.ej1.app.common.catalog.domain.port.UpdateCatalogCommand;
import com.ej1.app.common.catalog.domain.service.CatalogCacheNames;
import com.ej1.app.common.catalog.domain.service.CatalogCommandService;
import com.ej1.app.common.catalog.domain.service.CatalogQueryService;
import com.ej1.app.common.catalog.infrastructure.persistence.CatalogMapperAdapter;
import com.ej1.app.common.domain.Paging;
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
@SpringJUnitConfig({CatalogCacheConfig.class, CatalogQueryService.class, CatalogCommandService.class,
        CatalogMapperAdapter.class, CatalogCacheTest.TxConfig.class})
@TestPropertySource(properties = {
        "app.catalog.cache.maximum-size=100",
        "app.catalog.types.brand.cache.maximum-size=50",
        "app.catalog.types.brand.cache.expire-after-write=1h",
        "app.catalog.types.category.names.en=Category"  // sin cache propia: hereda la global
})
class CatalogCacheTest {

    private static final CatalogType BRAND = TestCatalogTypes.BRAND;
    private static final CatalogType CATEGORY = TestCatalogTypes.CATEGORY;
    private static final CatalogSearchCriteria NO_FILTER = new CatalogSearchCriteria(null, null);

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

    private final Catalog logitech = Catalog.builder().id(1L).type(BRAND)
            .translations(List.of(new CatalogTranslation("en", "Logitech", "desc"))).build();

    private final Catalog peripherals = Catalog.builder().id(1L).type(CATEGORY)
            .translations(List.of(new CatalogTranslation("en", "Peripherals", "desc"))).build();

    private final UpdateCatalogCommand rename = new UpdateCatalogCommand(0, Map.of(),
            List.of(new CatalogTranslationCommand("en", "Logitech Pro", "desc")));

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        when(repository.findById(BRAND, 1L)).thenReturn(Optional.of(logitech));
        when(repository.findById(CATEGORY, 1L)).thenReturn(Optional.of(peripherals));
        when(repository.existsById(BRAND, 1L)).thenReturn(true);
        when(repository.findAll(any(), any(), any(), any(), any())).thenReturn(List.of(logitech));
        when(repository.sortableExtraFields(any())).thenReturn(Set.of());
        when(repository.extraFieldTypes(any())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Cada tipo tiene sus propias cachés, más la global de tipos")
    void cachesArePerType() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(
                "catalogTypes", "catalogById:brand", "catalogLists:brand", "catalogById:category", "catalogLists:category");
    }

    @Test
    void findById_isCached() {
        assertThat(queryService.findById(BRAND, 1L)).isSameAs(logitech);
        assertThat(queryService.findById(BRAND, 1L)).isSameAs(logitech);

        verify(repository, times(1)).findById(BRAND, 1L);
        assertThat(cacheManager.getCache("catalogById:brand").get(1L)).isNotNull();
    }

    @Test
    @DisplayName("El mismo id en dos tipos son entradas distintas, cada una en la caché de su tipo")
    void findById_sameIdInTwoTypesDoesNotCollide() {
        assertThat(queryService.findById(BRAND, 1L)).isSameAs(logitech);
        assertThat(queryService.findById(CATEGORY, 1L)).isSameAs(peripherals);
        assertThat(queryService.findById(CATEGORY, 1L)).isSameAs(peripherals);

        verify(repository, times(1)).findById(BRAND, 1L);
        verify(repository, times(1)).findById(CATEGORY, 1L);
    }

    @Test
    @DisplayName("findById no se cachea cuando no existe: el 404 se vuelve a comprobar en cada petición")
    void findById_notFoundIsNotCached() {
        when(repository.findById(BRAND, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.findById(BRAND, 2L)).isInstanceOf(CatalogNotFoundException.class);
        assertThatThrownBy(() -> queryService.findById(BRAND, 2L)).isInstanceOf(CatalogNotFoundException.class);

        verify(repository, times(2)).findById(BRAND, 2L);
    }

    @Test
    @DisplayName("findAll sin filtro se cachea por (type, locale, criteria, sortBy, sortDirection)")
    void findAll_unfilteredIsCachedPerKey() {
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(BRAND, "es", NO_FILTER, "name", "ASC");

        verify(repository, times(1)).findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findAll(BRAND, "es", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("findAll con filtro de texto no se cachea")
    void findAll_filteredIsNotCached() {
        var criteria = new CatalogSearchCriteria("logi", null);
        queryService.findAll(BRAND, "en", criteria, "name", "ASC");
        queryService.findAll(BRAND, "en", criteria, "name", "ASC");

        verify(repository, times(2)).findAll(BRAND, "en", criteria, "name", "ASC");
    }

    @Test
    void findPage_isNotCached() {
        var paging = new Paging(0, 20, "name", "ASC");
        queryService.findPage(BRAND, "en", NO_FILTER, paging);
        queryService.findPage(BRAND, "en", NO_FILTER, paging);

        verify(repository, times(2)).findPage(BRAND, "en", NO_FILTER, paging);
    }

    @Test
    void findTypeDefinitions_isCached() {
        queryService.findTypeDefinitions();
        queryService.findTypeDefinitions();

        verify(repository, times(1)).extraFieldTypes(BRAND);
    }

    @Test
    @DisplayName("create vacía los listados de su tipo, no los de los demás")
    void create_evictsListsOfItsTypeOnly() {
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");

        commandService.create(new CreateCatalogCommand(BRAND, 0, Map.of(),
                List.of(new CatalogTranslationCommand("en", "Razer", "desc"))));
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");

        verify(repository, times(2)).findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("update invalida la entrada por id y los listados de su tipo, no las cachés de los demás")
    void update_evictsByIdAndListsOfItsTypeOnly() {
        queryService.findById(BRAND, 1L);
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findById(CATEGORY, 1L);
        queryService.findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");

        commandService.update(BRAND, 1L, rename);  // llama a repository.findById(BRAND, 1) por su cuenta
        queryService.findById(BRAND, 1L);
        queryService.findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        queryService.findById(CATEGORY, 1L);
        queryService.findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");

        verify(repository, times(3)).findById(BRAND, 1L);
        verify(repository, times(2)).findAll(BRAND, "en", NO_FILTER, "name", "ASC");
        verify(repository, times(1)).findById(CATEGORY, 1L);
        verify(repository, times(1)).findAll(CATEGORY, "en", NO_FILTER, "name", "ASC");
    }

    @Test
    @DisplayName("delete invalida la entrada por id")
    void delete_evictsById() {
        queryService.findById(BRAND, 1L);

        commandService.delete(BRAND, 1L);
        queryService.findById(BRAND, 1L);

        verify(repository, times(2)).findById(BRAND, 1L);
    }

    @Test
    @DisplayName("Un comando que falla (404) no invalida nada")
    void failedCommand_doesNotEvict() {
        queryService.findById(BRAND, 1L);
        when(repository.existsById(BRAND, 9L)).thenReturn(false);

        assertThatThrownBy(() -> commandService.delete(BRAND, 9L)).isInstanceOf(CatalogNotFoundException.class);
        queryService.findById(BRAND, 1L);

        verify(repository, times(1)).findById(BRAND, 1L);
    }

    @Test
    @DisplayName("Dentro de una transacción la invalidación espera al commit")
    void eviction_isDeferredUntilCommit() {
        queryService.findById(BRAND, 1L);                       // repo: 1

        tx.executeWithoutResult(status -> {
            commandService.update(BRAND, 1L, rename);           // repo: 2 (lectura propia del update)
            queryService.findById(BRAND, 1L);                   // aún en caché → repo: 2
            verify(repository, times(2)).findById(BRAND, 1L);
        });

        queryService.findById(BRAND, 1L);                       // tras el commit → repo: 3
        verify(repository, times(3)).findById(BRAND, 1L);
    }

    @Test
    @DisplayName("Si la transacción hace rollback, la caché no se invalida")
    void rollback_keepsCache() {
        queryService.findById(BRAND, 1L);                       // repo: 1

        tx.executeWithoutResult(status -> {
            commandService.update(BRAND, 1L, rename);           // repo: 2
            status.setRollbackOnly();
        });

        queryService.findById(BRAND, 1L);                       // sigue en caché → repo: 2
        verify(repository, times(2)).findById(BRAND, 1L);
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
