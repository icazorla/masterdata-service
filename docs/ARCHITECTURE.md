# Architecture

Este proyecto sigue una **Arquitectura Hexagonal estricta (Ports & Adapters)**. El núcleo es el dominio puro (sin Spring MVC ni JPA), y todo lo externo (REST, JPA, manejo de errores) entra a través de adaptadores que implementan los puertos definidos por el dominio.

Hoy la única feature es el **catálogo genérico de datos maestros** (`Catalog`): un mismo agregado, servicios, controllers y repositorio sirven a todos los tipos de catálogo (`brand`, `category`, ...), que se declaran por configuración en `application.yml` — ver [Catálogos genéricos](#catálogos-genéricos-un-agregado-para-todos-los-tipos).

La feature expone **dos APIs separadas** (CQRS ligero) — ver [API de consulta y API de gestión](#api-de-consulta-y-api-de-gestión): `/api/catalogs/{type}` (consulta, solo lectura, localizada) y `/admin/api/catalogs/{type}` (gestión, CRUD completo con todas las traducciones).

## Capas

```
+-----------------------------------------------------------------+
|  common/catalog/interfaces/rest        (HTTP entrante)          |
|    query/  CatalogQueryController  GET /api/catalogs/{type}[/{id}]|
|            CatalogTypeController   GET /api/catalogs            |
|    admin/  CatalogAdminController  /admin/api/catalogs/{type}[/{id}]|
|    CatalogTypePathConverter String → CatalogType (valida tipo)  |
|    dto/    CatalogRequest, CatalogResponse, CatalogAdminResponse,|
|            CatalogTranslationRequest/Response,                  |
|            @ValidCatalogTranslations (+ validator)              |
|    mapper/ CatalogRestMapper  (puerto; todo métodos default)    |
+-----------------------------------------------------------------+
                          |  CatalogRequest → Create/UpdateCatalogCommand
                          v  (CatalogRestMapper)
+-----------------------------------------------------------------+
|  common/catalog/domain                                  PURO    |
|    Catalog, CatalogTranslation, CatalogType   (agregado / VOs)  |
|    service/  CatalogQueryService    (casos de uso de lectura)   |
|              CatalogCommandService  (casos de uso de escritura) |
|    port/     CatalogRepository                (puerto salida)   |
|              CatalogMapper                    (Command→Catalog) |
|              CreateCatalogCommand, UpdateCatalogCommand,        |
|              CatalogTranslationCommand, CatalogSearchCriteria   |
|    exception/ CatalogNotFoundException                          |
|               DuplicateCatalogNameException                     |
|               InvalidCatalogQueryException                      |
+-----------------------------------------------------------------+
       ^                                            |
       | implementa                                 | invocado por
       |                                            v
+-----------------------------------------------------------------+
|  common/catalog/infrastructure                                  |
|    persistence/ CatalogRepositoryAdapter  (EntityManager + JPQL)|
|                 CatalogPersistenceConfig  (registro de tipos)   |
|                 CatalogProperties         (app.catalog.types.*) |
|                 CatalogTypeDescriptor, CatalogExtraFieldsAccessor|
|                 CatalogBaseEntity / CatalogTranslationBaseEntity|
|                 CatalogEntity / CatalogTranslationEntity (contratos)|
|                 CatalogMapperAdapter  (@Component de CatalogMapper)|
|    rest/        CatalogRestMapperAdapter (@Component del mapper REST)|
+-----------------------------------------------------------------+

Entidades JPA concretas (una pareja por tipo, en com.ej1.app.catalog):
    BrandEntity + BrandTranslationEntity
    CategoryEntity + CategoryTranslationEntity
```

`com.ej1.app.common` es el paquete transversal (no es una capa): contiene código sin dependencia a ningún agregado concreto. Se divide en subpaquetes con reglas de dependencia distintas — ver [Reglas de dependencia](#reglas-de-dependencia):

```
common/domain/Identifiable.java, Auditable.java       bases de agregado: id (+ createdAt/updatedAt)
common/domain/Paging.java                             page + size + sortBy + sortDirection (record puro)
common/domain/PageResult.java                         record genérico <T>: content + page + size + totalElements + totalPages
common/domain/SortableFieldValidator.java             requireSortable(sortBy, allowedFields, exceptionFactory)
common/domain/exception/NotFoundException.java       base → 404  (messageKey + args para i18n)
common/domain/exception/DuplicateResourceException.java  base → 409
common/domain/exception/InvalidQueryException.java   base → 422
common/infrastructure/persistence/BaseEntity.java, AuditableEntity.java   @MappedSuperclass: id (+ created_at/updated_at)
common/rest/SortDirection.java                        enum ASC/DESC (springdoc genera el enum en OpenAPI)
common/rest/PageHeaders.java                          PageHeaders.of(PageResult<?>) → X-Total-Count/X-Page-*
common/rest/exception/ApiExceptionHandler.java        @RestControllerAdvice global, i18n vía MessageSource
common/rest/exception/JsonPatchExceptionHandler.java  traduce las excepciones de JSON Patch
common/rest/patch/JsonPatchOperation.java             record: op, path, value, from
common/rest/patch/JsonPatch.java                      aplica las 6 operaciones RFC 6902 (add/remove/replace/move/copy/test)
common/rest/patch/JsonPatchApplier.java               orquesta: current → Map → patch → tipo original → Bean Validation
common/rest/patch/JsonPatchException.java             → 400
common/rest/patch/JsonPatchTestFailedException.java   → 409 (extiende JsonPatchException)
```

Además, `com.ej1.app.config.OpenApiConfig` (metadatos de Swagger + cabecera `Accept-Language` en cada operación).

> **Estado de `Paging`/`PageResult`/`PageHeaders`/`SortableFieldValidator`.** Todos los usa el listado de catálogos (`GET /api/catalogs/{type}`): `SortableFieldValidator` valida `sortBy` en `CatalogQueryService`, y con `paged=true` el controlador construye un `Paging`, `CatalogQueryService#findPage`/`CatalogRepository#findPage` devuelven un `PageResult<Catalog>` y la respuesta es la `List<T>` sin envolver con los metadatos en las cabeceras `X-Total-Count`/`X-Page-Number`/`X-Page-Size`/`X-Total-Pages` vía `PageHeaders.of(result)`. Con `paged=false` (por defecto en la API de consulta) se mantiene el listado completo sin paginar (`findAll`), pensado para poblar selects; la API de gestión pagina por defecto.

## Reglas de dependencia

| Capa              | Puede importar                              | NO puede importar                                  |
|-------------------|---------------------------------------------|----------------------------------------------------|
| `domain`          | `common.domain` (puro); `lombok`; excepción puntual: `@Service`/`@Transactional`/`@Cacheable`/`@CacheEvict` de Spring en `CatalogQueryService`/`CatalogCommandService` (anotaciones de inyección/transacciones/caché, no Spring MVC) | `infrastructure`, `interfaces`, `common.rest`, `org.springframework.web.*`/`org.springframework.data.*`, JPA, MapStruct |
| `common.domain`   | nada del proyecto (solo `lombok`)           | `domain` de features, `infrastructure`, `interfaces`, `common.rest`, cualquier framework |
| `common.rest`     | `common.domain`; Spring MVC, Jackson, Bean Validation | `domain`, `infrastructure`, `interfaces` de cualquier feature (nota: `ApiExceptionHandler` menciona `CatalogType` solo en Javadoc) |
| `interfaces`      | `domain`, `common` (domain + rest), Spring MVC | `infrastructure`, `org.springframework.data.jpa.*`; además `interfaces.rest.query` no puede usar `CatalogCommandService` |
| `infrastructure`  | `domain`, `common` (domain + rest), JPA, Spring | `interfaces`                                     |

**Idea clave**: el `domain` no sabe que existe Hibernate ni Spring MVC. Si mañana cambias JPA por otra tecnología de persistencia, o REST por gRPC, solo tocas `infrastructure` e `interfaces`.

## Catálogos genéricos: un agregado para todos los tipos

Antes había un agregado por catálogo (`Brand`, `Category`, cada uno con su service, controller, repositorio, mappers y DTOs, todos idénticos salvo el nombre). Se unificaron en uno solo:

- **`Catalog`** (dominio, inmutable, builder manual) lleva `type`, `translations` (una `CatalogTranslation(locale, name, description)` por idioma), `weight` (entero de ordenación manual, **no** traducido) y `extraFields` (`Map<String, Object>` con los campos propios de ese tipo, p. ej. `website` solo para `brand`).
- **`CatalogType`** no es un `enum`: sus valores se toman de `app.catalog.types` de `application.yml` — `CatalogPersistenceConfig` los registra al arrancar con `CatalogType.configure(...)`. `CatalogType.of("brand")` solo acepta tipos configurados (lanza `IllegalArgumentException` si no) y `CatalogType.values()` los lista en el orden del YAML. No hay constantes `BRAND`/`CATEGORY` en código.
- **Un único** juego de controllers (`CatalogQueryController`/`CatalogAdminController`), servicios (`CatalogQueryService`/`CatalogCommandService`) y `CatalogRepositoryAdapter` atiende a todos los tipos; el tipo llega como segmento de ruta (`/api/catalogs/{type}`, `/admin/api/catalogs/{type}`) y se pasa como parámetro explícito a cada método.

### Registro de tipos por configuración

```yaml
app:
  catalog:
    types:
      brand:
        entity-class: com.ej1.app.catalog.BrandEntity
        translation-entity-class: com.ej1.app.catalog.BrandTranslationEntity
        extra-fields: [website]
      category:
        entity-class: com.ej1.app.catalog.CategoryEntity
        translation-entity-class: com.ej1.app.catalog.CategoryTranslationEntity
```

`CatalogPersistenceConfig` convierte cada entrada en un `CatalogTypeDescriptor` (tipo + clases de entidad + nombres de campos extra) y publica el bean `Map<CatalogType, CatalogTypeDescriptor<?, ?>>` sobre el que despachan `CatalogRepositoryAdapter` y `CatalogTypePathConverter` (este último rechaza con 400 un `{type}` no configurado). Añadir un tipo nuevo = **dos clases `@Entity`** (una por tabla) + **una entrada en el YAML**; ninguna clase de repositorio/mapper/adaptador/controller nueva. Las clases se cargan con `Class.forName`, así que un nombre mal escrito falla al arrancar (`IllegalStateException`), no en compilación.

`CatalogRepositoryAdapter` está construido directamente sobre `EntityManager` (JPQL generado a partir de `Class#getSimpleName()` de un descriptor registrado por nosotros, nunca de la entrada del cliente) y opera sobre las entidades solo a través de los contratos estructurales `CatalogEntity<TE>`/`CatalogTranslationEntity<E>`, con métodos privados genéricos `<E, TE>`. No usa Spring Data ni MapStruct.

### Idiomas (i18n)

- `name`/`description` viven en filas de traducción por locale; toda entrada **debe** llevar la del idioma por defecto (`en`), y solo se aceptan `en`/`es` — lo impone `@ValidCatalogTranslations` en `CatalogRequest`.
- El cliente elige idioma con `Accept-Language`; el controller recibe `Locale` y `CatalogRestMapper.toResponse(catalog, locale)` resuelve `name`/`description` con fallback al idioma por defecto (`Catalog#resolveTranslation`).
- El filtro y el orden por `name`/`description` se evalúan **en base de datos** sobre la misma traducción resuelta (`coalesce(tLocale.campo, tDefault.campo)`), de modo que lo que se filtra/ordena coincide con lo que ve el cliente.
- La API de gestión (`/admin/api/catalogs/{type}`) devuelve en cambio todos los idiomas a la vez, indexados por locale (`CatalogAdminResponse.translations`, vía `CatalogRestMapper.toAdminResponse`).
- Los mensajes de error se localizan con `MessageSource` (`messages.properties` / `messages_es.properties`). Las excepciones de dominio llevan un `messageKey` + `args` (sin saber nada de i18n) y `ApiExceptionHandler` los resuelve; el `message` en inglés de la excepción es solo log/fallback.

### Campos adicionales por tipo

`extra-fields` lista atributos propios de un tipo (p. ej. `website` en `brand`). Deben existir como columna real con getter/setter JavaBean en la entidad; `CatalogExtraFieldsAccessor` los lee/escribe genéricamente. En el payload viajan como **atributos normales de primer nivel** (`{"website": "...", "translations": {...}}`), no anidados, gracias a `@JsonAnyGetter`/`@JsonAnySetter` en `CatalogRequest`/`CatalogResponse`. Se sobrescriben por completo en cada escritura (omitir uno existente lo borra) y también son válidos para `sortBy` (solo para el tipo que los declara).

### Endpoints

| API | Método | Ruta | Acción |
|-----|--------|------|--------|
| consulta | GET    | `/api/catalogs`                   | Tipos configurados y sus campos (`CatalogTypeController`) |
| consulta | GET    | `/api/catalogs/{type}`            | Listar, localizado (filtros `name`/`description`, `sortBy`, `sortDirection`; paginación opcional con `paged=true` + `page`/`size` y cabeceras `X-*`) |
| consulta | GET    | `/api/catalogs/{type}/{id}`       | Obtener por id, localizado (404 si no existe) |
| gestión  | GET    | `/admin/api/catalogs/{type}`      | Listar con todas las traducciones; mismos filtros/orden, pero `paged=true` por defecto |
| gestión  | GET    | `/admin/api/catalogs/{type}/{id}` | Obtener por id con todas las traducciones |
| gestión  | POST   | `/admin/api/catalogs/{type}`      | Crear (201 + `Location`) |
| gestión  | PUT    | `/admin/api/catalogs/{type}/{id}` | Actualización completa |
| gestión  | PATCH  | `/admin/api/catalogs/{type}/{id}` | JSON Patch RFC 6902 (`application/json-patch+json`) |
| gestión  | DELETE | `/admin/api/catalogs/{type}/{id}` | Eliminar (204; 409 si otra tabla lo referencia) |

Reglas de negocio (validación de `sortBy` en `CatalogQueryService`; unicidad en `CatalogCommandService`):
- **Nombre único por (tipo, locale)**, sin distinguir mayúsculas → `DuplicateCatalogNameException` (409), comprobado en `create` y `update` (excluyendo el propio id).
- **`sortBy` ordenable**: `ALLOWED_SORT_FIELDS = {name, description, weight}` + los `extra-fields` del tipo (`CatalogRepository#sortableExtraFields`), validado con `SortableFieldValidator.requireSortable(...)` → `InvalidCatalogQueryException` (422). `ALLOWED_SORT_FIELDS` es package-private para poder testearlo desde el mismo paquete.
- Si se dan `name` y `description` a la vez en el listado se combinan con **OR** (no AND).
- **`weight` es siempre el primer criterio de orden**, descendente, delante de `sortBy` — decisión de producto, no negociable por query param: construido en `CatalogRepositoryAdapter#orderByClauses` como `e.weight desc, <orderByExpression(sortBy)> <sortDirection>, e.id asc`. Excepción: si `sortBy` ya es `"weight"` no hay un segundo criterio que añadir, así que ahí sí manda `sortDirection` tal cual (un `"e.weight desc, e.weight asc"` sería ruido — dentro de cada grupo del primer criterio todas las filas comparten ya el mismo valor). `e.id asc` cierra como desempate final, igual que antes.

## API de consulta y API de gestión

La feature se publica como dos APIs con prefijo distinto, para que seguridad, gateway, caché o rate-limiting puedan tratarlas por separado con una sola regla de ruta (`/admin/**`):

| | Consulta | Gestión |
|---|---|---|
| Prefijo | `/api/catalogs` | `/admin/api/catalogs` |
| Controllers (`interfaces.rest.*`) | `query.CatalogQueryController`, `query.CatalogTypeController` | `admin.CatalogAdminController` |
| Servicios que usa | solo `CatalogQueryService` | `CatalogQueryService` + `CatalogCommandService` |
| Métodos | solo `GET` (el resto → 405 + `Allow`) | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| Respuesta | `CatalogResponse`: un `name`/`description` resuelto por `Accept-Language` | `CatalogAdminResponse`: `translations` con todos los locales (misma forma que `CatalogRequest`) |
| Listado | `paged=false` por defecto (selects) | `paged=true` por defecto |
| OpenAPI | grupo `consulta` → `/v3/api-docs/consulta` | grupo `gestion` → `/v3/api-docs/gestion` |

**CQRS ligero en el dominio.** El antiguo `CatalogService` se dividió en `CatalogQueryService` (`@Transactional(readOnly = true)` a nivel de clase: `findById`, `existsById`, `findAll`, `findPage`, `findTypeDefinitions`) y `CatalogCommandService` (`@Transactional`: `create`, `update`, `delete` + regla de nombre único). El controller de consulta solo recibe `CatalogQueryService`, así que no puede escribir por construcción. Ambos comparten el mismo puerto `CatalogRepository`. El reparto es también el que usa la [caché](#caché-de-lecturas): `@Cacheable` en las lecturas de `CatalogQueryService`, `@CacheEvict` en los comandos de `CatalogCommandService`.

**Documentación.** `OpenApiConfig` declara dos `GroupedOpenApi` (`consulta` → `/api/**`, `gestion` → `/admin/api/**`); Swagger UI muestra un desplegable para elegir. El `GlobalOperationCustomizer` de `Accept-Language` se aplica a ambos grupos.

**Errores.** `ApiExceptionHandler` es global y sirve a las dos APIs. Para que un cliente que siga escribiendo contra `/api/...` reciba un error útil (y no el 500 del catch-all), traduce explícitamente `HttpRequestMethodNotSupportedException` → 405 (con `Allow`) y `NoResourceFoundException` → 404.

**Siguiente paso posible:** una réplica de lectura para `CatalogQueryService`, desplegar cada API por separado (Maven multimódulo: `catalog-core` con dominio + persistencia, más un módulo por API), o añadir Spring Security con `/admin/api/**` → rol de administración.

## Caché de lecturas

Las lecturas de catálogo se cachean en memoria (hoy con **Caffeine**, `spring-boot-starter-cache` + `caffeine`). Los datos maestros se leen mucho y se escriben poco, y `Catalog` es inmutable, así que compartir la instancia cacheada es seguro. **La librería de caché está aislada detrás de un puerto**: cambiarla es añadir un adaptador, no tocar el resto.

**Cachés por tipo de catálogo.** Cada `CatalogType` tiene sus propias cachés, con su propia configuración; solo la de definiciones de tipos es global:

| Caché | Método (`CatalogQueryService`) | Clave | Se invalida en (`CatalogCommandService`) |
|-------|--------------------------|-------|------------------------|
| `catalogTypes` (global) | `findTypeDefinitions()` | — (única entrada) | nunca (sale de `application.yml`; solo cambia al reiniciar) |
| `catalogById:<type>` | `findById(type, id)` | `id` | `update`/`delete` de ese id |
| `catalogLists:<type>` | `findAll(...)` **solo sin filtro de texto** (`name`/`description` nulos) | (type, locale, criteria, sortBy, sortDirection) | `create`/`update`/`delete` de ese tipo → vacía la caché de listados **de ese tipo** |

Las anotaciones usan nombres *base* (`CatalogCacheNames.BY_ID`/`LISTS`); ambos servicios declaran `@CacheConfig(cacheResolver = "catalogCacheResolver")` y `CatalogCacheResolver` los traduce a la caché del tipo de la llamada, tomando el `CatalogType` de los argumentos (o `CreateCatalogCommand.type()` en `create`). Una escritura en `brand` no toca las cachés de `category`.

### Aislamiento de la librería de caché

```
infrastructure/cache/
  CatalogCacheConfig      ← independiente: lee la config, pide cada caché al proveedor, SimpleCacheManager + TransactionAwareCacheManagerProxy
  CatalogCacheResolver    ← independiente (solo org.springframework.cache)
  CatalogCachesEndpoint   ← independiente: config de las propiedades + tamaño/estadísticas de Micrometer
  spi/
    CacheSpec             ← record neutro: maximumSize, expireAfterWrite, expireAfterAccess, stats, nativeOptions
    CatalogCacheProvider  ← puerto: String id(); Cache create(String name, CacheSpec spec)
  caffeine/
    CaffeineCatalogCacheProvider  ← único sitio que importa com.github.benmanes.caffeine
    CaffeineCacheProviderConfig   ← @ConditionalOnClass(Caffeine) + @ConditionalOnProperty(provider=caffeine, por defecto)
```

- El dominio y `CatalogCacheResolver` ya solo dependían de la abstracción de Spring (`@Cacheable`, `Cache`, `CacheManager`); lo que conocía Caffeine (montaje del gestor, sintaxis de configuración y endpoint) pasa a estar detrás de `CatalogCacheProvider`.
- `CatalogCacheConfig` busca el proveedor cuyo `id()` coincide con `app.catalog.cache.provider`; si no hay ninguno (librería ausente o id mal escrito) **falla al arrancar** con la lista de los disponibles.
- **Contrato de un proveedor** (Javadoc de `CatalogCacheProvider`): respetar todo `CacheSpec` y fallar ante una opción que no soporte (nunca ignorarla); devolver la `Cache` estándar de Spring para su librería, sin envolver (es la que Spring Boot conecta a Micrometer); no admitir `null`; cachear **por referencia** (`Catalog` no es `Serializable`: una librería que copie por valor, como JCache por defecto, debe configurarse por referencia).
- **Añadir una librería** (p. ej. cache2k o Ehcache vía JCache): un subpaquete con su `CatalogCacheProvider` y su `@Configuration` condicional, una línea en el `@Import` de `CatalogCacheConfig`, la dependencia en el `pom.xml` y `app.catalog.cache.provider: <id>`. Nada más cambia.

### Configuración

```yaml
app:
  catalog:
    cache:                        # valores por defecto + proveedor + configuración de catalogTypes
      provider: caffeine
      enabled: true
      maximum-size: 1000
      expire-after-write: 10m
      stats: true
    types:
      category:
        cache:                    # solo lo que cambia; el resto se hereda campo a campo
          maximum-size: 500
          expire-after-write: 1h
          # enabled: false
          # native-options: { initialCapacity: "64" }
```

| Campo | Significado | Por defecto (global) |
|-------|-------------|----------------------|
| `provider` | id del `CatalogCacheProvider` (solo global) | `caffeine` |
| `enabled` | `false` → no se crean las cachés; `CatalogCacheResolver` da un `NoOpCache` | `true` |
| `maximum-size` | máximo de entradas (ausente = sin límite) | `1000` |
| `expire-after-write` | caducidad desde la escritura (ausente = no caduca) | `10m` |
| `expire-after-access` | caducidad por inactividad | — |
| `stats` | registrar aciertos/fallos/expulsiones (Micrometer, `/actuator/catalogcaches`) | `true` |
| `native-options` | vía de escape específica de la librería (Caffeine: claves de `CaffeineSpec`). Se fusiona con la global (gana el tipo). Repetir aquí un campo neutro, o una clave desconocida, falla al arrancar | — |

- La herencia es **campo a campo** (`CacheSettings#effectiveFor`): un tipo que solo indica `expire-after-write` conserva el `maximum-size` global. Limitación: un tipo no puede *quitar* un valor global (p. ej. dejar sin caducidad si la global la tiene); para eso, no lo pongas en la global.
- `enabled: false` en `app.catalog.cache` desactiva `catalogTypes` y todos los tipos que no indiquen `enabled: true`.
- El gestor es **estático**: solo existen las cachés configuradas; un nombre desconocido falla en vez de crear una caché sin configurar.

**Qué no se cachea:** `findPage` y los listados filtrados (combinaciones de texto libre × página × tamaño que apenas se repiten), los 404 (las excepciones no se cachean) ni un `sortBy` inválido (lanza 422 antes de cachear).

**Por qué vaciar todos los listados del tipo:** un cambio puede mover la entrada en cualquier listado de su tipo (nombre, peso, orden) y esas claves no se pueden enumerar. Las escrituras son raras, así que es barato.

**Invalidación al commit:** el `SimpleCacheManager` va envuelto en un `TransactionAwareCacheManagerProxy`. Los `evict`/`clear` de un comando se aplican solo si la transacción hace commit: un rollback (p. ej. un `delete` que falla por FK) no vacía la caché, y una lectura concurrente no puede recachear el valor antiguo entre la invalidación y el commit.

**Limitación:** la invalidación es **local al proceso**. Con varias instancias de la app, o si la base se modifica por fuera (script SQL), otra instancia puede servir datos obsoletos hasta que caduquen (`expire-after-write` del tipo). Si eso no es aceptable, la salida es un proveedor distribuido (Redis, Hazelcast…) — que sí exige serializar `Catalog`.

**Estado en Actuator:**
- `GET /actuator/catalogcaches` — `CatalogCachesEndpoint` (endpoint propio de solo lectura, **independiente de la librería**): el proveedor, la configuración global, la caché `catalogTypes` y cada tipo con su configuración **efectiva** (`settings`, ya heredada) y, por caché, `active`, `size` y `stats` (`requests`, `hits`, `misses`, `hitRate`, `evictions`, acumuladas desde el arranque). Tamaño y estadísticas salen de **Micrometer** (`cache.size`, `cache.gets`, `cache.evictions` con la etiqueta `cache=<nombre>`), que Spring Boot conecta para las librerías que soporta; si no hay métricas, esos campos van a `null` en vez de inventar ceros. `stats` es `null` con `stats: false`; `hitRate` es `null` mientras no haya peticiones. `GET /actuator/catalogcaches/{type}` devuelve un solo tipo (404 si no existe); las cachés de un tipo desactivado salen con `active: false`.
- `GET /actuator/caches` — endpoint estándar de Spring (solo nombres y clase). Expuesto con `management.endpoint.caches.access: read-only`: su `DELETE` (vaciar cachés) devuelve 405 mientras Actuator no tenga seguridad delante; para habilitarlo, `access: unrestricted`.
- Métricas Micrometer directas: p. ej. `/actuator/metrics/cache.gets?tag=cache:catalogLists:brand&tag=result:hit`.
- Al declarar su propio `CacheManager`, `CatalogCacheConfig` hace que la autoconfiguración de caché de Spring Boot se retire: `spring.cache.*` no aplica.

## Inversión de dependencias (Ports)

```
domain/port/CatalogRepository.java                        ← interface (puerto)
    ^
    | implementa
    |
infrastructure/persistence/CatalogRepositoryAdapter.java  (@Repository, EntityManager)
    |
    | opera sobre (vía descriptor)
    v
com.ej1.app.catalog.BrandEntity / CategoryEntity (+ *TranslationEntity)   (JPA)
```

`CatalogQueryService` recibe `CatalogRepository`, y `CatalogCommandService` `CatalogRepository` + `CatalogMapper`, por constructor. Spring descubre `CatalogRepositoryAdapter` (`@Repository`) y `CatalogMapperAdapter` (`@Component`) y los inyecta.

## Mapeos entre capas

| Origen                              | Destino                     | Quién mapea                                   | Dónde vive |
|-------------------------------------|-----------------------------|-----------------------------------------------|------------|
| HTTP JSON                           | `CatalogRequest`            | Jackson (auto)                                | `interfaces.rest.dto` |
| `CatalogRequest` (+ `type` de la ruta) | `CreateCatalogCommand`   | `CatalogRestMapper.toCreateCommand(type, request)` | `interfaces.rest.mapper` (puerto) / `infrastructure.rest.CatalogRestMapperAdapter` |
| `CatalogRequest`                    | `UpdateCatalogCommand`      | `CatalogRestMapper.toUpdateCommand`           | igual |
| `Create/UpdateCatalogCommand`       | `Catalog`                   | `CatalogMapper.toDomain` / `applyUpdate`      | `domain.port` (puerto) / `infrastructure.persistence.CatalogMapperAdapter` |
| `Catalog`                           | entidad JPA                 | `CatalogRepositoryAdapter` (`createEntity`/`mergeIntoExisting`) | `infrastructure.persistence` (manual, reflexivo) |
| entidad JPA                         | `Catalog`                   | `CatalogRepositoryAdapter#toDomain`           | `infrastructure.persistence` |
| `Catalog` + `Locale`                | `CatalogResponse` (consulta) | `CatalogRestMapper.toResponse` / `toResponseList` | `interfaces.rest.mapper` |
| `Catalog`                           | `CatalogAdminResponse` (gestión) | `CatalogRestMapper.toAdminResponse` / `toAdminResponseList` | `interfaces.rest.mapper` |
| `List<CatalogTranslation>`          | `Map<locale, CatalogTranslationResponse>` | `CatalogRestMapper.toTranslationResponses` (usado por `toAdminResponse`) | `interfaces.rest.mapper` |
| `Catalog`                           | `CatalogRequest` (base del PATCH) | `CatalogRestMapper.toRequest`           | `interfaces.rest.mapper` |

Hay **dos mappers** a propósito, cada uno cubre una sola frontera:

| Mapper (puerto) | Frontera | Implementación (bean) | Quién lo usa |
|-----------------|----------|------------------------|--------------|
| `CatalogRestMapper` (`interfaces.rest.mapper`) | HTTP ↔ dominio (`CatalogRequest`/`CatalogResponse` ↔ `Command`/`Catalog`) | `CatalogRestMapperAdapter` (`infrastructure.rest`) | `CatalogQueryController`, `CatalogAdminController`, `CatalogTypeController` |
| `CatalogMapper` (`domain.port`) | Command ↔ `Catalog` (dentro del dominio) | `CatalogMapperAdapter` (`infrastructure.persistence`) | `CatalogCommandService` |

La conversión `Catalog ↔ entidad JPA` no tiene mapper: es un detalle interno de `CatalogRepositoryAdapter` (necesita conocer el descriptor del tipo para instanciar la entidad correcta), no un contrato de dominio.

Ambos puertos son **interfaces sin anotaciones** cuyos métodos son todos `default` — las conversiones `Map<locale, …> ↔ List<…Command>` y la resolución por `Locale` no son derivables solo de nombres de propiedad, así que ya no hay nada que MapStruct pueda autogenerar. Los adapters (`@Component`, cuerpo vacío) existen únicamente para dar al controller/servicio un bean inyectable, manteniendo el puerto libre de Spring. (MapStruct sigue en el `pom.xml`, pero el código actual no lo usa.)

`createdAt`/`updatedAt` los genera Hibernate (`@CreationTimestamp`/`@UpdateTimestamp`) en `CatalogBaseEntity`; `CatalogRepositoryAdapter#toDomain` los copia al agregado al leer. En `save()` de una entrada existente, `weight`, campos extra y traducciones se **fusionan in situ** sobre la entidad gestionada (dirty checking, sin `merge`/`persist` explícito): reemplazar la colección de traducciones violaría transitoriamente la unicidad `(locale, name)` dentro del mismo flush.

## Manejo de errores

`ApiExceptionHandler` (`common/rest/exception`, `@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`) es **global y compartido por todas las features**. Traduce a HTTP los tipos base de dominio, de modo que una excepción nueva que extienda uno de ellos obtiene su código gratis, sin handler nuevo:

| Excepción                           | HTTP | Cuándo |
|-------------------------------------|------|--------|
| `NotFoundException` (`CatalogNotFoundException`) | 404 | `findById`/`update`/`delete` sin match |
| `DuplicateResourceException` (`DuplicateCatalogNameException`) | 409 | `create`/`update` con nombre ya existente en ese tipo+idioma |
| `InvalidQueryException` (`InvalidCatalogQueryException`) | 422 | `sortBy` no ordenable para ese tipo |
| `DataIntegrityViolationException`   | 409  | La fila está referenciada por otra tabla (FK) o carrera en una restricción única |
| `MethodArgumentTypeMismatchException` | 400 | Path/query param no convertible (p. ej. `{type}` desconocido, `sortDirection` ≠ ASC/DESC) |
| `MethodArgumentNotValidException`   | 400  | Bean Validation en `@Valid @RequestBody` |
| `ConstraintViolationException`      | 400  | Bean Validation programática (resultado de un `PATCH`) |
| `HttpMessageNotReadableException`   | 400  | JSON malformado |
| `HttpMediaTypeNotSupportedException`| 415  | `Content-Type` no soportado |
| `HttpRequestMethodNotSupportedException` | 405 | Método no soportado en esa ruta (p. ej. `POST /api/catalogs/brand`, la API de consulta es de solo lectura); incluye la cabecera `Allow` |
| `NoResourceFoundException`          | 404  | Ninguna ruta coincide |
| Cualquier otra                      | 500  | Fallback |

`JsonPatchException` (400) y `JsonPatchTestFailedException` (409) las traduce `JsonPatchExceptionHandler` (`@Order(0)`), no `ApiExceptionHandler`.

**Gotcha de Spring con dos `@ControllerAdvice`:** Spring no compara especificidad de `@ExceptionHandler` *entre* advices — usa el primer advice (en orden) con *algún* handler aplicable, aunque sea el catch-all `Exception.class`. Por eso `ApiExceptionHandler` va con `@Order(LOWEST_PRECEDENCE)` y `JsonPatchExceptionHandler` con `@Order(0)`: sin esto el catch-all interceptaba `JsonPatchTestFailedException` y devolvía 500 en vez de 409.

Formato de respuesta (mensaje localizado según `Accept-Language`; `errors` solo aparece si hay detalle):

```json
{
  "timestamp": "2026-09-30T12:34:56Z",
  "status": 404,
  "error": "Not Found",
  "message": "Brand not found: id=999",
  "errors": []
}
```

## PATCH parcial — JSON Patch (RFC 6902)

`PATCH /admin/api/catalogs/{type}/{id}` (`Content-Type: application/json-patch+json`) aplica un documento [RFC 6902](https://www.rfc-editor.org/rfc/rfc6902) sobre la entrada. Todo el soporte vive en `common/rest`, no en la feature, para que cualquier otro agregado con un `PATCH` lo reutilice.

**Por qué `JsonPatch` no usa una librería de terceros.** Spring Boot 4.1 corre el stack web sobre **Jackson 3** (`tools.jackson.databind`), pero toda librería `json-patch` publicada (java-json-tools, zjsonpatch, fge…) está escrita contra `JsonNode` de **Jackson 2**; añadirla habría significado cargar un segundo stack de Jackson. `JsonPatch.apply(...)` opera sobre `Map`/`List`/escalares genéricos — la forma que produce cualquier `ObjectMapper.convertValue(x, Map.class)` — sin importar tipos de Jackson. Es una clase `final` estática, sin Spring, testeable con JUnit puro (`JsonPatchTest`).

`JsonPatchApplier` (`@Component`) es quien sí conoce Jackson y Bean Validation: `apply(T current, List<JsonPatchOperation> patch)` convierte `current` a `Map`, delega en `JsonPatch`, reconvierte al tipo original y lo valida con `jakarta.validation.Validator` (→ `ConstraintViolationException` si el resultado es inválido).

**Flujo en `CatalogAdminController.patch(...)`:**
1. `mapper.toRequest(existing)` — proyecta el `Catalog` actual sobre la forma editable `CatalogRequest` (sin `id`/timestamps: ninguna operación puede tocarlos, fallan con 400 "member does not exist"). `translations` es un objeto indexado por locale, así que se direcciona directo: `/translations/es`, `/translations/en/description`.
2. `jsonPatchApplier.apply(current, patch)` — conversión, patch, reconversión y validación (incluida `@ValidCatalogTranslations`: quitar el idioma por defecto → 400).
3. `commandService.update(type, id, mapper.toUpdateCommand(patchedRequest))` — reutiliza el caso de uso existente, así que un nombre duplicado tras el patch sigue dando 409.

## Tests por capa

| Test | Capa | Tecnología | Tests |
|------|------|------------|-------|
| `CatalogQueryServiceTest` | `domain.service` | Mockito (`@Mock` repo) | 14 |
| `CatalogCommandServiceTest` | `domain.service` | Mockito (`@Mock` repo + `@Spy` mapper) | 7 |
| `CatalogQueryControllerTest` | `interfaces.rest.query` | `@WebMvcTest` + MockMvc, `CatalogRestMapperAdapter`/`ApiExceptionHandler` reales; incluye 405 en escrituras | 22 |
| `CatalogTypeControllerTest` | `interfaces.rest.query` | `@WebMvcTest` + MockMvc | 2 |
| `CatalogAdminControllerTest` | `interfaces.rest.admin` | `@WebMvcTest` + MockMvc, `CatalogRestMapperAdapter`/`ApiExceptionHandler`/`JsonPatchExceptionHandler` reales | 31 |
| `CatalogRepositoryAdapterTest` | `infrastructure.persistence` | `@DataJpaTest` + H2 (test scope), `CatalogRepositoryAdapter` + `CatalogPersistenceConfig` reales | 69 |
| `CatalogTypeNamesMessageSourceTest` | `infrastructure.i18n` | JUnit puro | 4 |
| `CatalogCacheTest` | `infrastructure.cache` | `@SpringJUnitConfig` con los proxies reales de caché + transacción + `CatalogCacheResolver`, repositorio `@MockitoBean` y un `PlatformTransactionManager` sin recurso (cachés por tipo, invalidación por tipo, al commit/rollback). Independiente de la librería | 14 |
| `CatalogCacheSettingsTest` | `infrastructure.cache` | JUnit + `ApplicationContextRunner`: valores de serie, herencia campo a campo, `CacheSpec` que recibe el proveedor (con un proveedor de prueba), proveedor por defecto y fallo con un proveedor desconocido | 6 |
| `CaffeineCatalogCacheProviderTest` | `infrastructure.cache.caffeine` | JUnit puro: `CacheSpec` → Caffeine (límites, caducidades, stats, `nativeOptions`, conflictos y claves desconocidas fallan) | 6 |
| `CatalogCacheDisabledTest` | `infrastructure.cache` | Igual, con `enabled` global/por tipo (herencia, `NoOpCache`) | 4 |
| `CatalogCachesEndpointTest` | `infrastructure.cache` | `@SpringJUnitConfig(CatalogCacheConfig)` + `SimpleMeterRegistry`: informe de `/actuator/catalogcaches` (configuración efectiva, tamaño/stats de Micrometer, sin registro, tipo desactivado, selector) | 7 |
| `JsonPatchTest` | `common.rest.patch` | JUnit puro, sin Spring | 19 |
| `ApplicationTests` | global | `@SpringBootTest` | 1 |

Notas: el controller test usa el mapper REST real (no un mock: mockearlo daba respuestas vacías) para verificar también serialización JSON y traducción Request↔Command; el test de repositorio importa `CatalogPersistenceConfig` para validar el registro de tipos y el JPQL end-to-end contra una base real (H2 en tests; PostgreSQL en ejecución).

## Cómo añadir un nuevo tipo de catálogo

Ejemplo: añadir `color`.

1. **Dos entidades JPA** en `com.ej1.app.catalog`: `ColorEntity extends CatalogBaseEntity implements CatalogEntity<ColorTranslationEntity>` y `ColorTranslationEntity` (extiende `CatalogTranslationBaseEntity`, implementa `CatalogTranslationEntity<ColorEntity>`). Copia `BrandEntity`/`BrandTranslationEntity` como plantilla; cualquier columna propia del tipo va como campo con getter/setter.
2. **Una entrada en `application.yml`** bajo `app.catalog.types` (`entity-class`, `translation-entity-class`, y `extra-fields` si hay columnas propias).
3. **(Opcional) Nombre traducido** en la misma entrada del YAML: `names: {en: Color, es: Color}`. Los errores 404/409 usan las claves genéricas `error.catalog.notFound`/`error.catalog.duplicateName` con el `CatalogType` como argumento (implementa `MessageSourceResolvable`, clave `catalog.type.<code>`). `CatalogI18nConfig` engancha `CatalogTypeNamesMessageSource`, que sirve esas claves desde `names`, como padre del `messageSource` de Spring Boot. Sin traducción para un idioma se usa el código capitalizado (`Color`).
4. **(Opcional) Caché propia** en la misma entrada: `cache: {maximum-size: ..., expire-after-write: ..., enabled: ...}`; sin ella hereda `app.catalog.cache`. Sus cachés (`catalogById:color`, `catalogLists:color`) se crean solas a partir de la configuración — ver [Caché de lecturas](#caché-de-lecturas).

No hace falta tocar controllers, servicios, repositorio, mappers ni DTOs: `/api/catalogs/color` (consulta) y `/admin/api/catalogs/color` (gestión, con CRUD y PATCH) funcionan con i18n y ordenación. Para una entidad que **no** es un catálogo de nombre+descripción (con reglas y campos propios), la guía paso a paso del patrón hexagonal por agregado está en [NUEVA_ENTIDAD.md](NUEVA_ENTIDAD.md).
