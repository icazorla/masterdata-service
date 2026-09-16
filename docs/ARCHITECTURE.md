# Architecture

Este proyecto sigue una **Arquitectura Hexagonal estricta (Ports & Adapters)**. El núcleo es el dominio puro (sin Spring ni JPA), y todo lo externo (REST, JPA, manejo de errores) entra a través de adaptadores que implementan los puertos definidos por el dominio.

## Capas

```
+--------------------------------------------------------------+
|  interfaces/rest      ProductController  (HTTP entrante)     |
|                       dto/        ProductRequest, Response   |
|                       dto/        ProductQueryRequest         |
|                       mapper/     ProductRestMapper          |
|                         toResponseList(...): usa               |
|                         shared.rest.PageHeaders — GET y POST   |
|                         /query devuelven List<ProductResponse> |
|                         + cabeceras X-Total-Count/X-Page-*     |
+--------------------------------------------------------------+
                          |
                          |  ProductRequest → Command
                          |  ProductQueryRequest → ProductSearchCriteria
                          v   (REST mapper)
+--------------------------------------------------------------+
|  domain                                              PURO     |
|  +-----------------+   Product             (agregado)        |
|  | service/        |   ProductService      (casos de uso)    |
|  +-----------------+                                          |
|  | port/           |   ProductRepository   (interface, usa   |
|  |                 |     shared.domain.PageResult<Product>)   |
|  |                 |   ProductMapper       (interface)       |
|  |                 |   CreateProductCommand (record)          |
|  |                 |   UpdateProductCommand (record)          |
|  |                 |   ProductSearchCriteria (record, solo    |
|  |                 |     filtros — paging va aparte, ver más  |
|  |                 |     abajo)                                |
|  +-----------------+                                          |
|  | exception/      |   ProductNotFoundException               |
|  |                 |   DuplicateSkuException                  |
|  |                 |   InvalidProductQueryException           |
|  +-----------------+                                          |
+--------------------------------------------------------------+
       ^                                            |
       | implementa                                 | invocado por
       |                                            v
+--------------------------------------------------------------+
|  infrastructure                                              |
|  +-----------------------+  ProductEntityMapperImpl           |
|  | persistence/          |  ProductEntity          (JPA)      |
|  |                       |  ProductJpaRepository   (Spring)   |
|  |                       |  ProductRepositoryAdapter          |
|  |                       |  ProductSpecifications             |
|  +-----------------------+                                    |
|  | exception/            |  ApiExceptionHandler                |
|  |                       |   (@RestControllerAdvice)          |
|  +-----------------------+                                    |
+--------------------------------------------------------------+
```

`com.ej1.app.shared` (no está en el diagrama porque no es una capa, es un paquete transversal)
contiene código sin dependencia a ningún agregado, reutilizable por cualquier feature. Se divide
en dos subpaquetes con reglas de dependencia distintas — ver más abajo:

```
shared/domain/Paging.java                       page + size + sortBy + sortDirection — agrupados,
                                                 sin ningún framework (record puro)
shared/domain/PageResult.java                   record genérico <T>: content + page + size +
                                                 totalElements + totalPages — resultado de cualquier
                                                 *Repository#search, sin ningún framework
shared/domain/SortableFieldValidator.java       requireSortable(sortBy, allowedFields, exceptionFactory):
                                                 comprueba pertenencia + formatea el mensaje + lanza la
                                                 excepción de dominio de quien la llama; el propio set de
                                                 campos y el tipo de excepción siguen siendo de cada
                                                 feature (ver ProductService)
shared/rest/PageHeaders.java                    PageHeaders.of(PageResult<?>): Consumer<HttpHeaders> con
                                                 X-Total-Count/X-Page-Number/X-Page-Size/X-Total-Pages —
                                                 para endpoints que devuelven List<T> sin envolver (ver
                                                 sección RFC 10008 más abajo)
shared/rest/patch/JsonPatchOperation.java       record: op, path, value, from
shared/rest/patch/JsonPatch.java                aplica las 6 operaciones RFC 6902 (add/remove/replace/move/copy/test)
shared/rest/patch/JsonPatchApplier.java         orquesta: current → Map → patch → tipo original → Bean Validation
shared/rest/patch/JsonPatchException.java       → 400
shared/rest/patch/JsonPatchTestFailedException.java  → 409 (extiende JsonPatchException)
shared/rest/exception/JsonPatchExceptionHandler.java  @RestControllerAdvice, traduce las dos anteriores
```

`Paging` lo usan hoy `ProductRepository.search`/`ProductService.search`, como parámetro
independiente de `ProductSearchCriteria` (que ya no lo lleva dentro — ver más abajo); cuando se
añada una segunda entidad con búsqueda paginada, su propio `*Service`/`*Repository.search` lo
reutiliza igual, sin repetir los cuatro campos. No hay un DTO REST equivalente:
`page`/`size`/`sortBy`/`sortDirection` viajan como **query params**
en cada endpoint de búsqueda (`GET /api/products`, `POST /api/products/query`), con Bean Validation
(`@RequestParam` + `@PositiveOrZero`/`@Min`/`@Max`/`@Pattern`) directamente en la firma del
controller — ver la sección RFC 10008 más abajo. El propio controller construye el `Paging` con
`new Paging(page, size, sortBy, sortDirection)` una vez Spring ha resuelto los defaults y validado
el formato; no hace falta un mapper para ese paso.

`PageResult<T>` va un paso más allá que `Paging`: no solo agrupa campos repetidos, está
**parametrizado** — a diferencia de `page`/`size`/`sortBy`/`sortDirection` (siempre
primitivos/String), la forma completa de "una página de resultados" (`content` + las cuatro
métricas) es idéntica para cualquier agregado, cambiando solo el tipo de elemento. Por eso ya no
existe `ProductPage`: `ProductRepository.search` devuelve `PageResult<Product>` directamente.
Cuando se añada una segunda entidad paginada, su propio `*Repository` reutiliza
`PageResult<Order>` sin declarar ningún record nuevo.

Este proyecto expone **una única** forma de página en HTTP, y es deliberadamente la más simple:
todo endpoint de listado (`GET /api/products`, `POST /api/products/query`) devuelve el body sin
envolver (`List<ProductResponse>`, vía `ProductRestMapper.toResponseList`, que reutiliza
`toResponse(Product)` elemento a elemento) y manda los metadatos de página
(`X-Total-Count`/`X-Page-Number`/`X-Page-Size`/`X-Total-Pages`) en cabeceras HTTP, construidas por
`PageHeaders.of(result)` a partir del mismo `PageResult<Product>` — ver la sección RFC 10008 más
abajo para el porqué de esa elección y el detalle del flujo. No existe ya un DTO de wrapper
(`PageResponse<T>`/`ProductPageResponse`): con una sola convención de respuesta paginada para
todos los endpoints de listado, un wrapper record no aportaba nada que las cabeceras + la lista no
resolvieran ya, así que se eliminó en vez de mantenerse sin uso.

El paquete `shared/rest/patch` (+ `shared/rest/exception`) sigue la misma idea que `Paging`/
`PageResult`/`PageHeaders` para JSON Patch: es vocabulario RFC 6902 puro, sin nada
de `Product`, así que cualquier feature con un endpoint `PATCH` inyecta `JsonPatchApplier` y hereda
gratis la traducción de errores — ver la sección dedicada más abajo.

## Reglas de dependencia

| Capa              | Puede importar                              | NO puede importar                                  |
|-------------------|---------------------------------------------|----------------------------------------------------|
| `domain`          | `shared.domain` (value objects puros, sin frameworks); nada más del proyecto (+ `lombok`); excepción puntual: `org.springframework.stereotype.Service`/`org.springframework.transaction.annotation.Transactional` en `ProductService` (anotaciones de inyección/transacciones, no Spring MVC) | `infrastructure`, `interfaces`, `shared.rest`, `org.springframework.web.*`/`org.springframework.data.*` (Spring MVC/Data), `org.mapstruct.*` |
| `shared.domain`   | nada del proyecto (solo `lombok`, si acaso) | `domain`, `infrastructure`, `interfaces`, `shared.rest`, cualquier framework |
| `shared.rest`     | `shared.domain` (p. ej. `PageHeaders` lee un `PageResult<?>`); Spring MVC, Jackson, Bean Validation (nada de ningún agregado) | `domain`, `infrastructure`, `interfaces` de cualquier feature |
| `interfaces`      | `domain`, `shared` (domain + rest), Spring MVC | `infrastructure`, `org.springframework.data.jpa.*` |
| `infrastructure`  | `domain`, `shared` (domain + rest), JPA, Spring | `interfaces`                                     |

**Por qué `shared` está partido en `domain`/`rest` en vez de ser un único paquete.** El `domain`
de cualquier feature no puede depender de nada Spring/Jackson-bound — esa es la regla clave de la
arquitectura hexagonal de este proyecto (ver más abajo). Un `shared` monolítico habría obligado a
elegir entre "domain no puede reutilizar nada de shared" (duplicando `page`/`size`/`sortBy`/
`sortDirection` en cada `*Service#search`) o "shared puede colar Spring Data en el domain" (rompe
la regla). Separar `shared.domain` (Java puro) de `shared.rest` (Spring MVC/Jackson) deja que el
`domain` importe el primero sin tocar el segundo.

**Idea clave**: el `domain` no sabe que existe Hibernate ni Spring MVC. Si mañana cambias JPA por MyBatis, o REST por gRPC, solo tocas `infrastructure` y `interfaces`.

El `@Mapper(componentModel = "spring")` que llevan las interfaces `ProductMapper` y `ProductRestMapper` es solo metadatos para que MapStruct genere el `Impl` con `@Component`. La anotación MapStruct no es dependencia lógica — la implementación que se inyecta en runtime ya está en el paquete correcto según quién la declara.

## Inversión de dependencias (Ports)

```
domain/port/ProductRepository.java                       ← interface (puerto)
    ^
    | implementa
    |
infrastructure/persistence/ProductRepositoryAdapter.java
    |
    | usa
    v
infrastructure/persistence/ProductJpaRepository.java     (Spring Data)
    |
    v
infrastructure/persistence/ProductEntity.java            (JPA annotated)
```

`ProductService` recibe `ProductRepository` por constructor. Spring Boot descubre `ProductRepositoryAdapter` (anotado con `@Repository`) y lo inyecta.

## Mapeos entre capas

| Origen                          | Destino              | Quién mapea                  | Dónde vive                                |
|---------------------------------|----------------------|------------------------------|-------------------------------------------|
| HTTP JSON                       | `ProductRequest`     | Jackson (auto)               | `interfaces.rest.dto`                     |
| `ProductRequest`                | `CreateProductCommand` / `UpdateProductCommand` | `ProductRestMapper.toCreateCommand` / `toUpdateCommand` | `interfaces.rest.mapper` (puerto) / `infrastructure.rest.ProductRestMapperAdapter` (impl MapStruct) |
| `CreateProductCommand` / `UpdateProductCommand` | `Product` | `ProductMapper.toDomain`     | `domain.port` (puerto) / `infrastructure.persistence.ProductMapperAdapter` (impl MapStruct) |
| `UpdateProductCommand`          | `Product` (update)   | `ProductMapper.applyUpdate`  | `domain.port` (default method, sin MapStruct — ver más abajo) |
| `Product`                       | `ProductEntity`      | `ProductEntityMapper.toEntity` | `infrastructure.persistence` (interface + impl MapStruct, mismo paquete) |
| `ProductEntity`                 | `Product`            | `ProductEntityMapper.toDomain` | `infrastructure.persistence` (interface + impl MapStruct, mismo paquete) |
| `Product`                       | `ProductResponse`    | `ProductRestMapper.toResponse` | `interfaces.rest.mapper` (puerto) / `infrastructure.rest.ProductRestMapperAdapter` (impl MapStruct) |
| `ProductQueryRequest`           | `ProductSearchCriteria` | `ProductRestMapper.toCriteria` | igual que la fila anterior (campo a campo — ambos son solo filtros) |
| `ProductSearchCriteria`         | `Specification<ProductEntity>` | `ProductSpecifications.from` | `infrastructure.persistence` (manual, no MapStruct) |
| `List<Product>` (`result.content()`) | `List<ProductResponse>` | `ProductRestMapper.toResponseList` | igual que `toResponse` (reutiliza el método elemento a elemento; usado por `findAll` y `query`, paging va en cabeceras vía `PageHeaders`) |

Hay **tres mappers** diferentes a propósito — cada uno cubre exactamente una frontera entre capas y ninguno hace el trabajo de otro:

| Mapper (puerto)            | Cruza la frontera        | Implementación MapStruct (dónde vive el `@Mapper`) | Quién lo inyecta/usa                          |
|-----------------------------|---------------------------|------------------------------------------------------|------------------------------------------------|
| `ProductRestMapper` (`interfaces.rest.mapper`) | HTTP ↔ dominio (`ProductRequest`/`ProductQueryRequest`/`ProductResponse` ↔ `Command`/`Product`/`ProductSearchCriteria`) | `ProductRestMapperAdapter` (`infrastructure.rest`) | `ProductController` — es el único punto de entrada/salida HTTP |
| `ProductMapper` (`domain.port`)                | Command ↔ `Product` (dentro del dominio, sin tocar HTTP ni JPA) | `ProductMapperAdapter` (`infrastructure.persistence`) | `ProductService` — lo necesita para construir/actualizar el agregado a partir de un Command |
| `ProductEntityMapper` (`infrastructure.persistence`, sin puerto en `domain`) | `Product` ↔ `ProductEntity` (dominio ↔ JPA) | Misma interface, en el mismo paquete (no hay adapter separado: al no ser un contrato de dominio, no necesita vivir tras un puerto) | `ProductRepositoryAdapter` — es el único que conoce `ProductEntity` |

Ningún mapper conoce más de una frontera: `ProductRestMapper` nunca ve `ProductEntity`, y `ProductEntityMapper` nunca ve `ProductRequest`/`ProductResponse`. Por eso hacen falta los tres — fusionar cualquier par obligaría a esa capa a conocer un tipo que no le corresponde (p. ej. `ProductRestMapper` tendría que importar `ProductEntity`, rompiendo la regla de dependencia de `interfaces`).

`ProductMapper` y `ProductRestMapper` son **interfaces de puerto sin anotaciones** (el dominio/interfaces no pueden depender de MapStruct/Spring): cada una tiene un adapter en `infrastructure` que la extiende vacía y añade `@Mapper(componentModel = "spring")` — MapStruct genera la implementación (`ProductMapperAdapterImpl`/`ProductRestMapperAdapterImpl`) a partir de esa interfaz vacía. `ProductEntityMapper` es la excepción: al ser un detalle interno del adaptador JPA sin contrato de dominio, la interface y el `@Mapper` viven juntos, directamente en `infrastructure.persistence`, sin necesidad de un adapter intermedio.

Los timestamps `createdAt`/`updatedAt` los genera Hibernate (`@CreationTimestamp`/`@UpdateTimestamp`) sobre `ProductEntity`. El adapter los copia al dominio al leer, y al actualizar el `id` y los timestamps se preservan vía `Product.withIdAndTimestamps(...)`.

> **Importante**: `ProductEntityMapper.toEntity` mapea explícitamente `id` (con `@Mapping(target = "id", source = "id")`) para que las actualizaciones hagan `merge` y no `insert`. Sin esto, Hibernate trataría cada `save()` de un producto con `id` como nuevo y rompería la unicidad del SKU.

## Manejo de errores

`ApiExceptionHandler` vive en `infrastructure/exception/` porque depende de Spring (`@RestControllerAdvice`). Traduce excepciones del dominio a HTTP:

| Excepción de dominio      | HTTP | Cuándo                                            |
|---------------------------|------|---------------------------------------------------|
| `ProductNotFoundException`| 404  | `findById`/`findBySku`/`update`/`delete` sin match |
| `DuplicateSkuException`   | 409  | `create`/`update` con SKU ya existente            |
| `InvalidProductQueryException` | 422 | `POST /api/products/query`: la consulta es sintácticamente válida pero semánticamente inconsistente (p. ej. `minPrice > maxPrice`) |
| `MethodArgumentNotValidException` | 400 | Bean Validation falla en `ProductRequest`/`ProductQueryRequest` (`@Valid @RequestBody`) |
| `ConstraintViolationException` | 400 | Bean Validation falla al validar programáticamente (p. ej. el resultado de un `PATCH`) |
| Cualquier otra            | 500  | Fallback (`ApiExceptionHandler`, `@Order(LOWEST_PRECEDENCE)`) |

`JsonPatchException` (400) y `JsonPatchTestFailedException` (409) — errores al aplicar un documento JSON Patch — **no** los traduce `ApiExceptionHandler`. Viven y se traducen en `shared/rest`, ver más abajo.

El formato de respuesta es:

```json
{
  "timestamp": "2026-08-07T12:34:56Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found: id=999",
  "errors": []
}
```

## Búsqueda de productos — semántica RFC 10008 (HTTP QUERY)

`POST /api/products/query` ejecuta una búsqueda filtrada/ordenada/paginada cuyos **filtros**
viajan en el **cuerpo** de la petición en vez de en la URL, siguiendo la idea del método HTTP
`QUERY` definido en [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html) ("The HTTP QUERY
Method"): una petición *safe* (no muta estado) e idempotente, pero con cuerpo como un POST. La
paginación y el orden (`page`/`size`/`sortBy`/`sortDirection`), en cambio, viajan como **query
params** — igual que en `GET /api/products` — porque son metadatos de "cómo entregar el
resultado", no parte de la consulta en sí; solo lo que filtra qué productos coinciden va en el
cuerpo. El cuerpo de la **respuesta** sigue la misma idea: es la lista de productos sin envolver
(`List<ProductResponse>`), y los metadatos de la página (total de elementos, página/tamaño
aplicados, total de páginas) viajan en las cabeceras `X-Total-Count`/`X-Page-Number`/
`X-Page-Size`/`X-Total-Pages` en vez de en un wrapper JSON — ver el porqué más abajo.

**Por qué es `POST` y no el verbo `QUERY` real.** Spring Framework (Boot 4.1 → Spring Framework
7.0.8) todavía no soporta el método `QUERY` de forma nativa — `RequestMethod` solo tiene
`GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS/TRACE`. El soporte llega en Spring Framework 7.1
(~noviembre 2026; ver [spring-framework#34993](https://github.com/spring-projects/spring-framework/pull/34993)).
Añadirlo hoy exigiría un `RequestMappingHandlerMapping` y un `DispatcherServlet` propios que
interceptasen el verbo `QUERY` antes de que `HttpServlet` lo rechace — código de infraestructura
no trivial, frágil frente al cambio de versión de Spring, y que además exige preflight CORS
porque `QUERY` no es un método "safelisted" para navegadores. El endpoint conserva en cambio la
**semántica** del RFC (cuerpo con la consulta, misma forma de respuesta, 200/400/415/422) sobre
el verbo `POST`, ya soportado de forma estable en todo el stack (Spring, Tomcat, proxies,
navegadores). Migrar al verbo `QUERY` cuando Spring 7.1 lo soporte será un cambio aislado al
`@RequestMapping` del controller.

```
interfaces/rest/dto/ProductQueryRequest.java     solo filtros (name, sku, category, brand, minPrice,
                                                  maxPrice, active, tags) — sin paginación/orden
domain/port/ProductSearchCriteria.java           record de dominio, agnóstico de HTTP/JPA — solo filtros,
                                                  sin paging (ver más abajo por qué)
shared/domain/Paging.java                        page + size + sortBy + sortDirection — parámetro aparte de
                                                  ProductSearchCriteria en ProductRepository#search/
                                                  ProductService#search, reutilizable por cualquier *Service
shared/domain/PageResult.java                    record genérico <T>: content + page + size + totalElements +
                                                  totalPages — devuelto por cualquier *Repository#search
                                                  (ver más arriba)
shared/rest/PageHeaders.java                     PageHeaders.of(result): cabeceras X-Total-Count/X-Page-*
                                                  a partir de un PageResult<?> (ver más arriba)
infrastructure/persistence/ProductSpecifications.java   ProductSearchCriteria → Specification<ProductEntity>
```

**Por qué la paginación y el orden viajan como query params (`?page=0&size=20&sortBy=price&sortDirection=DESC`)
y no dentro del cuerpo.** `POST /api/products/query` combina dos cosas de naturaleza distinta: *qué*
productos coinciden (los filtros — eso es la "consulta" que RFC 10008 justifica mandar en el
cuerpo) y *cómo* se entrega el resultado (paginación/orden — un detalle de presentación, no de
la consulta en sí). Tratar ambos igual como query params, exactamente igual que en
`GET /api/products`, mantiene una única convención en todo el controller para "cómo pido una
página" en vez de dos (uno para GET, otro anidado en el body de POST), y deja que Spring resuelva
los defaults y el formato con la misma anotación `@RequestParam(defaultValue = ...)` + Bean
Validation (`@PositiveOrZero`/`@Min`/`@Max`/`@Pattern`) que ya usa `findAll`.

**Por qué la página de resultados va como `List<ProductResponse>` + cabeceras
(`X-Total-Count`/`X-Page-Number`/`X-Page-Size`/`X-Total-Pages`) en vez de un wrapper JSON —
en `findAll` y en `query` por igual.** Con la paginación/orden ya fuera del cuerpo/como query params
(ver más arriba), aplicar la misma idea a la salida evita forzar a todo consumidor de cualquiera de
los dos endpoints a desenvolver un `{"content": [...], ...}` cuando solo le interesa la lista; los
metadatos de paginación, que son cabecera por naturaleza en muchas APIs REST (mismo patrón que
`Link`/`X-Total-Count` en GitHub, Django REST Framework, etc.), quedan disponibles sin tocar el
body para quien sí los necesita. Es una única convención de respuesta paginada para todo el
controller — `findAll` y `query` comparten exactamente el mismo mecanismo:
`ProductRestMapper.toResponseList` mapea elemento a elemento reutilizando `toResponse(Product)`, y
`PageHeaders.of(result)` (`shared.rest`) construye las cuatro cabeceras a partir del mismo
`PageResult<Product>` — ver el flujo más abajo. Existió un `PageResponse<T>` wrapper en un diseño
anterior (usado solo por `findAll`); se eliminó al alinear ambos endpoints porque, con una sola
convención de respuesta paginada en todo el controller, ya no tenía ningún consumidor.

**Por qué `sortBy` no lleva ningún `@Pattern`/enum en el controller, a pesar de que `page`, `size`
y `sortDirection` sí llevan su propia restricción ahí mismo.** El conjunto de nombres de campo
válidos para `sortBy` es conocimiento de `Product` (o de la entidad que sea), no de la paginación en
general. `page`, `size` y `sortDirection` sí son universales (cualquier página tiene un tamaño entre
1 y 100; cualquier orden es `ASC` o `DESC`), así que esos tres sí se validan con Bean Validation
normal en la firma del método. `sortBy` viaja sin restricción hasta `ProductService.search`, que lo
valida contra su propio `ALLOWED_SORT_FIELDS` como regla de negocio — ver más abajo. El propio
`ProductService` delega el mecanismo de "comprobar pertenencia + formatear mensaje + lanzar" en
`SortableFieldValidator.requireSortable(sortBy, ALLOWED_SORT_FIELDS, InvalidProductQueryException::new)`
(`shared.domain`, ver el listado de archivos más arriba): la utilidad no conoce `Product` ni HTTP —
recibe el `Set` de campos válidos y una fábrica de excepción como parámetros — así que una segunda
entidad con su propio `*Service.search` la reutiliza sin copiar el `if`, pasando su propio `Set` y su
propia excepción de dominio.

`ALLOWED_SORT_FIELDS` es paquete-privado (no `private`) precisamente para que
`ProductServiceSortFieldsTest`, en el mismo paquete, pueda verificarlo por reflexión: comprueba que
cada nombre del `Set` sigue teniendo un getter real en `Product`. Es un `Set<String>` escrito a
mano sin ningún enlace en tiempo de compilación con los campos de `Product` — nada impide
renombrar un campo sin acordarse de actualizar el `Set`, y el desajuste no se notaría hasta que un
`sortBy` para ese campo fallase más abajo (`ProductSpecifications`/JPA) en vez de con un 422 claro.
Este test convierte ese silencio en un fallo inmediato y legible. Cualquier segunda entidad que
declare su propio `ALLOWED_SORT_FIELDS` debería añadir el mismo test — no está automatizado a nivel
de `shared` a propósito: el problema (nombres de campo de un agregado concreto) es tan específico
de cada entidad como el propio `Set`, así que no hay una utilidad genérica razonable que lo
sustituya sin acoplarse a un agregado.

**Por qué `ProductSearchCriteria` ya no lleva `paging` dentro, y `ProductRepository`/
`ProductService#search` lo reciben como parámetro aparte.** Antes `ProductSearchCriteria` agrupaba
filtros + `Paging` en un único record, y todo el que necesitaba paginar tenía que leerlo de
`criteria.paging()`. El problema es que, con `page`/`size`/`sortBy`/`sortDirection` ya resueltos
por el controller como query params (ver más arriba) y los filtros llegando por separado en el
body, forzar a `ProductSearchCriteria` a cargar con ambos habría exigido reconstruirlo con el
`Paging` correcto en cada punto de entrada (`findAll` y `query`) antes de poder llamar a
`service.search`, con dos representaciones potencialmente inconsistentes de "qué página" si algo
se le olvidaba actualizar. Separarlos en dos parámetros de método hace que cada uno tenga una única
fuente de verdad: `ProductSearchCriteria` es *solo* "qué coincide", `Paging` es *solo* "cómo se
entrega", y no hay forma de que diverjan porque no hay redundancia que mantener sincronizada.

**Flujo en `ProductController.query(...)`:**
1. Spring resuelve `page`/`size`/`sortBy`/`sortDirection` desde los query params (con sus defaults
   y su Bean Validation de formato) y deserializa el cuerpo JSON a `ProductQueryRequest` (solo
   filtros). El controller construye `new Paging(page, size, sortBy, sortDirection)` directamente —
   no hace falta un método de mapeo para esto: los tipos y nombres ya coinciden uno a uno con el
   constructor de `Paging`.
2. `mapper.toCriteria(request)` — traduce los filtros del body a `ProductSearchCriteria`. MapStruct
   resuelve el mapeo campo a campo sin ninguna anotación `@Mapping` manual: ambos tipos tienen
   exactamente las mismas propiedades de filtro, mismo nombre.
3. `service.search(criteria, paging)` — valida dos reglas de negocio que Bean Validation no puede
   expresar con una anotación de campo: `minPrice > maxPrice` (regla cruzada entre dos campos de
   `criteria`) y `sortBy` (de `paging`) no está en `ProductService.ALLOWED_SORT_FIELDS`
   (conocimiento propio de `Product`). Ambas lanzan `InvalidProductQueryException` → 422, y delega
   en el repositorio si pasan.
4. `ProductRepositoryAdapter.search(criteria, paging)` — construye un `Specification<ProductEntity>`
   vía `ProductSpecifications.from(criteria)` (filtros `LIKE` case-insensitive para
   `name`/`sku`/`category`/`brand`/`tags`, rango para `price`, igualdad para `active`; los filtros
   ausentes no restringen nada) y un `PageRequest` a partir de `paging` para el orden/paginación, y
   mapea el `Page<ProductEntity>` (Spring Data) resultante a `PageResult<Product>` (`shared.domain`,
   ver más arriba). `category`/`brand` son `List<String>` en vez de `String`: cada valor de la lista
   se evalúa con el mismo `LIKE` case-insensitive de siempre y los resultados se combinan con OR
   (`containsIgnoreCaseAnyOf` en `ProductSpecifications`), de modo que ese grupo OR se AND-ea con el
   resto de filtros igual que haría un único valor.
5. `mapper.toResponseList(result.content())` — proyecta la lista de `Product` a
   `List<ProductResponse>`, reutilizando `toResponse(Product)` elemento a elemento. Las cabeceras
   `X-Total-Count`/`X-Page-Number`/`X-Page-Size`/`X-Total-Pages` las añade `PageHeaders.of(result)`
   (`shared.rest`, ver el listado de archivos más arriba): un `Consumer<HttpHeaders>` que
   `ResponseEntity.BodyBuilder#headers(Consumer)` aplica directamente, sin cuatro llamadas a
   `.header(...)` repetidas en el controller ni un mapper (son cuatro valores escalares leídos de
   `PageResult`, no una traducción de tipo). Reutilizable por cualquier otro endpoint, de cualquier
   feature, que devuelva una lista sin envolver — no hace falta declarar nada nuevo por entidad.

`ProductController.findAll` (`GET /api/products`) sigue exactamente el mismo flujo, con dos
diferencias mínimas: no hay paso 2 (`ProductSearchCriteria` se construye directamente con los 8
filtros a `null`, no hay body que mapear) y no lleva la cabecera `Accept-Query` (es un `GET`
corriente, no la semántica RFC 10008 de `query`). Los pasos 3-5 —validación de negocio, búsqueda,
`toResponseList` + `PageHeaders`— son literalmente los mismos.

**Por qué `ProductSpecifications` no encadena `Specification.where(null).and(...)`.** Es la forma
"de manual" de combinar specs opcionales, pero en la versión de Spring Data JPA de este proyecto
un criterio totalmente vacío produce una `Specification` que es `null` en tiempo de ejecución, y
`SimpleJpaRepository.findAll(Specification, Pageable)` rechaza explícitamente un `Specification`
nulo (`IllegalArgumentException: Specification must not be null`). En su lugar, `from(...)` filtra
los componentes no nulos con `Stream`/`Objects::nonNull` y hace `reduce(Specification::and)`, con
un `cb.conjunction()` (siempre verdadero) como último recurso cuando no hay ningún filtro.

**400 vs 422, y por qué ambos existen a la vez.** RFC 10008 distingue contenido que no coincide
con el `Content-Type` declarado (400) de contenido que el servidor entiende pero rechaza por
motivos de negocio (422 "media type understood but query content invalid"). Aquí eso se traduce
en dos mecanismos distintos: las restricciones puramente de formato, universales para cualquier
entidad (`@Pattern` en `sortDirection`, `@Min`/`@Max` en `size`, `@PositiveOrZero` en los precios...)
siguen la convención ya existente en el proyecto y devuelven 400 vía Bean Validation; las reglas que
dependen de conocimiento propio de `Product` — `minPrice > maxPrice` (cruza dos campos) y `sortBy`
siendo o no un campo ordenable de producto (una lista de valores válidos que ninguna clase compartida
podría fijar de antemano) — no son expresables con una anotación de campo en un DTO reutilizable y
viven como excepción de dominio dedicada (`InvalidProductQueryException`) en `ProductService`,
devolviendo 422. `GET /api/products` sigue exactamente la misma división de responsabilidades para
sus propios parámetros `page`/`size`/`sortBy`/`sortDirection` — llegan al mismo
`ProductService.search`, así que heredan el mismo 400/422.

## PATCH parcial — JSON Patch (RFC 6902)

`PATCH /api/products/{id}` (`Content-Type: application/json-patch+json`) aplica un documento
[RFC 6902](https://www.rfc-editor.org/rfc/rfc6902) sobre el producto existente. Todo el soporte
vive en `shared/rest` (ver más arriba), no en `product`, precisamente para que cualquier otro
agregado con un endpoint `PATCH` lo reutilice sin duplicar código ni volver a resolver estos mismos
problemas:

```
shared/rest/patch/JsonPatchOperation.java              record: op, path, value, from
shared/rest/patch/JsonPatch.java                        aplica las 6 operaciones (add/remove/replace/move/copy/test)
shared/rest/patch/JsonPatchApplier.java                 orquesta conversión + patch + validación (ver más abajo)
shared/rest/patch/JsonPatchException.java               → 400
shared/rest/patch/JsonPatchTestFailedException.java     → 409 (extiende JsonPatchException)
shared/rest/exception/JsonPatchExceptionHandler.java    @RestControllerAdvice, traduce las dos anteriores
```

**Por qué `JsonPatch` no usa una librería de terceros.** Spring Boot 4.1 corre el stack web sobre
**Jackson 3** (`tools.jackson.databind`), pero toda librería `json-patch` publicada (java-json-tools,
zjsonpatch, fge…) está escrita contra `com.fasterxml.jackson.databind.JsonNode` de **Jackson 2**.
Añadir cualquiera de ellas habría significado cargar un segundo stack de Jackson solo para esto.
En su lugar, `JsonPatch.apply(...)` opera sobre `Map`/`List`/escalares genéricos — la misma forma
que produce cualquier `ObjectMapper.convertValue(x, Map.class)`, sea Jackson 2 o 3 — sin importar
ningún tipo de Jackson. Es una clase `final` estática, sin Spring, deliberadamente agnóstica de
framework — se puede seguir testeando con JUnit puro (`JsonPatchTest`) sin levantar contexto.

**`JsonPatchApplier` (`@Component`, `shared/rest/patch`) es quien sí conoce Jackson y Bean
Validation**, precisamente para que ningún controller repita el mismo baile de 4 pasos
(convertir a `Map` → aplicar → reconvertir → validar) a mano. Expone un único método:

```java
public <T> T apply(T current, List<JsonPatchOperation> patch)
```

que convierte `current` a `Map`, delega la aplicación en `JsonPatch.apply(...)`, reconvierte el
resultado al tipo original y lo valida con el `jakarta.validation.Validator` inyectado — lanzando
`ConstraintViolationException` si el resultado incumple alguna restricción Bean Validation. Se
mantiene como clase separada de `JsonPatch` a propósito: `JsonPatch` sigue siendo la
implementación pura de RFC 6902 (sin Jackson, sin Spring), mientras que `JsonPatchApplier` es la
orquestación consciente de framework que usan los controllers.

**Flujo en `ProductController.patch(...)`:**
1. `mapper.toRequest(existing)` — proyecta el `Product` actual sobre la forma editable de `ProductRequest` (sin `id`/`createdAt`/`updatedAt`: quedan fuera del documento, así que ninguna operación puede tocarlos — cualquiera que lo intente falla con 400 "member does not exist").
2. `jsonPatchApplier.apply(current, patch)` — hace conversión, aplicación del patch, reconversión y validación en una sola llamada (ver arriba); ante fallo lanza `JsonPatchException`/`JsonPatchTestFailedException` (→ 400/409, vía `JsonPatchExceptionHandler`) o `ConstraintViolationException` (→ 400, vía `ApiExceptionHandler`).
3. `service.update(id, mapper.toUpdateCommand(patchedRequest))` — reutiliza el caso de uso existente tal cual, así que un SKU duplicado tras el patch sigue devolviendo 409 (`DuplicateSkuException`) sin código nuevo.

**Por qué `JsonPatchExceptionHandler` es un `@RestControllerAdvice` aparte, no un método más en `ApiExceptionHandler`.** Un documento JSON Patch (`op`/`path`/`value`) es vocabulario puramente REST/HTTP que nunca llega al dominio de ningún agregado — a diferencia de `ProductNotFoundException` o `DuplicateSkuException`, que sí son de `Product`. Vive junto a `JsonPatch`/`JsonPatchApplier` en `shared.rest`, así que se registra una única vez y cualquier feature que use `JsonPatchApplier` hereda la traducción a HTTP sin declarar nada — `ApiExceptionHandler` (específico de `product`, en `product.infrastructure.exception`) no tendría sentido como dueño de un error que no es de `Product`.

**Gotcha de Spring con dos `@ControllerAdvice`:** Spring no compara especificidad de `@ExceptionHandler` *entre* advices — para una excepción dada, usa el primer advice (en orden) que tenga *algún* handler aplicable, aunque sea el `catch-all Exception.class` de otro advice menos específico. Por eso `ApiExceptionHandler` lleva `@Order(Ordered.LOWEST_PRECEDENCE)` y `JsonPatchExceptionHandler` lleva `@Order(0)`: sin esto, el `catch-all` de `ApiExceptionHandler` interceptaba `JsonPatchTestFailedException` antes de que su propio advice la viera, devolviendo 500 en vez de 409.

## Tests por capa

| Test                                      | Capa           | Tecnología                | Cobertura                         |
|-------------------------------------------|----------------|---------------------------|-----------------------------------|
| `ProductServiceTest`                      | `domain.service`| Mockito (`@Mock` repo + `@Spy` mapper) — 17 tests | Casos de uso, validaciones, mapeos, `search` (delegación + reglas `minPrice > maxPrice` y `sortBy` ordenable) |
| `ProductServiceSortFieldsTest`            | `domain.service`| JUnit puro + reflexión — 1 test | `ALLOWED_SORT_FIELDS` sigue nombrando getters reales de `Product` (evita que un rename desincronice el whitelist en silencio) |
| `ProductControllerTest`                   | `interfaces.rest`| `@WebMvcTest` + MockMvc + `ProductRestMapperAdapterImpl` real — 30 tests | HTTP flows, validación Bean, `@RestControllerAdvice`, PATCH (200/400/404/409), GET paginado (200/400/422), QUERY (200/400/415/422) |
| `JsonPatchTest`                           | `shared.rest.patch`| JUnit puro, sin Spring — 19 tests | Las 6 operaciones RFC 6902, ejemplos del propio RFC, casos de error, atomicidad |
| `ProductRepositoryAdapterTest`            | `infrastructure.persistence`| `@DataJpaTest` + H2 + `ProductEntityMapperImpl` real — 16 tests | Persistencia + mapeo entity↔domain (incluido update con `id` preservado), `search` (filtros, orden, paginación contra H2 real) |
| `ApplicationTests`                        | global         | `@SpringBootTest` — 1 test | Contexto completo arranca limpio  |

### Notas sobre los tests

- **Controller**: el `ProductRestMapperAdapterImpl` se inyecta como bean real (vía `@Import`), no como mock. Mockear el mapper daba respuestas vacías porque `toResponse` devolvía `null`. Mantenerlo real verifica también la serialización JSON y la traducción Request↔Command.
- **Repository**: importa `ProductEntityMapperImpl` para validar el mapping end-to-end contra la base de datos, incluyendo la preservación del `id` en updates.
- **Service**: mockea el `ProductRepository` y usa `Mappers.getMapper(ProductMapper.class)` como `@Spy` para verificar el flujo de mapping Command↔Product sin depender de la entity JPA.

## Cómo añadir una nueva entidad

Ejemplo: añadir `Order`. Repite los mismos pasos:

1. **`domain/Order.java`**: POJO inmutable con `@Builder` manual (como `Product`).
2. **`domain/exception/...`**: `OrderNotFoundException`, etc.
3. **`domain/port/OrderRepository.java`**: interface de dominio.
4. **`domain/port/CreateOrderCommand.java`** y **`UpdateOrderCommand.java`**: records del dominio.
5. **`domain/port/OrderMapper.java`**: interface MapStruct con `@Mapper(componentModel = "spring")`. Métodos `toDomain(Command)`, `applyUpdate(...)`. El impl se genera automáticamente.
6. **`domain/service/OrderService.java`**: `@Service`, depende de las interfaces anteriores.
7. **`interfaces/rest/dto/OrderRequest.java`** + **`OrderResponse.java`**: con `@Valid` donde corresponda.
8. **`interfaces/rest/mapper/OrderRestMapper.java`**: interface MapStruct con `toResponse`, `toCreateCommand`, `toUpdateCommand`.
9. **`infrastructure/persistence/OrderEntity.java`** + **`OrderJpaRepository.java`** + **`OrderRepositoryAdapter.java`** + **`OrderEntityMapper.java`** (interface MapStruct para `Order ↔ OrderEntity`).
10. **`interfaces/rest/OrderController.java`**: endpoints (inyecta `OrderRestMapper` y `OrderService`).
11. **Tests**: `OrderServiceTest` (en `domain/service/`), `OrderControllerTest` (en `interfaces/rest/`, importa `OrderRestMapperImpl`), `OrderRepositoryAdapterTest` (en `infrastructure/persistence/`, importa `OrderEntityMapperImpl`).

`ApiExceptionHandler` ya cubre cualquier excepción nueva con el fallback 500; añade `@ExceptionHandler` específico si necesitas un código distinto.