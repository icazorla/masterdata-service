# Architecture

Este proyecto sigue una **Arquitectura Hexagonal estricta (Ports & Adapters)**. El núcleo es el dominio puro (sin Spring ni JPA), y todo lo externo (REST, JPA, manejo de errores) entra a través de adaptadores que implementan los puertos definidos por el dominio.

## Capas

```
+--------------------------------------------------------------+
|  interfaces/rest      ProductController  (HTTP entrante)     |
|                       dto/        ProductRequest, Response   |
|                       dto/        ProductQueryRequest,       |
|                                   ProductPageResponse         |
|                       mapper/     ProductRestMapper          |
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
|  | port/           |   ProductRepository   (interface)       |
|  |                 |   ProductMapper       (interface)       |
|  |                 |   CreateProductCommand (record)          |
|  |                 |   UpdateProductCommand (record)          |
|  |                 |   ProductSearchCriteria (record)         |
|  |                 |   ProductPage          (record)          |
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

`com.ej1.app.shared.rest` (no está en el diagrama porque no es una capa, es un paquete
transversal) contiene código de transporte HTTP sin dependencia a ningún agregado, reutilizable
por cualquier feature:

```
shared/rest/dto/PagingRequest.java              number + size + sortBy + sortDirection — agrupados en "page"
shared/rest/patch/JsonPatchOperation.java       record: op, path, value, from
shared/rest/patch/JsonPatch.java                aplica las 6 operaciones RFC 6902 (add/remove/replace/move/copy/test)
shared/rest/patch/JsonPatchApplier.java         orquesta: current → Map → patch → tipo original → Bean Validation
shared/rest/patch/JsonPatchException.java       → 400
shared/rest/patch/JsonPatchTestFailedException.java  → 409 (extiende JsonPatchException)
shared/rest/exception/JsonPatchExceptionHandler.java  @RestControllerAdvice, traduce las dos anteriores
```

`PagingRequest` lo usa hoy `ProductQueryRequest.page`; cuando se añada una segunda entidad con
búsqueda paginada, su propio `*QueryRequest` lo reutiliza en vez de duplicarlo. El paquete
`shared/rest/patch` (+ `shared/rest/exception`) sigue la misma idea para JSON Patch: es
vocabulario RFC 6902 puro, sin nada de `Product`, así que cualquier feature con un endpoint
`PATCH` inyecta `JsonPatchApplier` y hereda gratis la traducción de errores — ver la sección
dedicada más abajo.

## Reglas de dependencia

| Capa              | Puede importar                              | NO puede importar                                  |
|-------------------|---------------------------------------------|----------------------------------------------------|
| `domain`          | nada del proyecto (solo `lombok`)           | `infrastructure`, `interfaces`, `shared`, `org.springframework.*`, `org.mapstruct.*` |
| `shared`          | Spring MVC, Jackson, Bean Validation (nada de ningún agregado) | `domain`, `infrastructure`, `interfaces` de cualquier feature |
| `interfaces`      | `domain`, `shared`, Spring MVC              | `infrastructure`, `org.springframework.data.jpa.*` |
| `infrastructure`  | `domain`, `shared`, JPA, Spring             | `interfaces`                                       |

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
| `ProductRequest`                | `CreateProductCommand` / `UpdateProductCommand` | `ProductRestMapper.toCreateCommand` / `toUpdateCommand` | `interfaces.rest.mapper` (MapStruct) |
| `CreateProductCommand` / `UpdateProductCommand` | `Product` | `ProductMapper.toDomain`     | `domain.port` (MapStruct, mismo paquete) |
| `UpdateProductCommand`          | `Product` (update)   | `ProductMapper.applyUpdate`  | `domain.port` (default method)            |
| `Product`                       | `ProductEntity`      | `ProductEntityMapper.toEntity` | `infrastructure.persistence` (MapStruct) |
| `ProductEntity`                 | `Product`            | `ProductEntityMapper.toDomain` | `infrastructure.persistence` (MapStruct) |
| `Product`                       | `ProductResponse`    | `ProductRestMapper.toResponse` | `interfaces.rest.mapper` (MapStruct)      |
| `ProductQueryRequest`           | `ProductSearchCriteria` | `ProductRestMapper.toCriteria` | `interfaces.rest.mapper` (MapStruct, defaults en `ProductRestMapperAdapter`) |
| `ProductSearchCriteria`         | `Specification<ProductEntity>` | `ProductSpecifications.from` | `infrastructure.persistence` (manual, no MapStruct) |
| `ProductPage`                   | `ProductPageResponse`| `ProductRestMapper.toPageResponse` | `interfaces.rest.mapper` (MapStruct, reutiliza `toResponse`) |

Hay **dos mappers** diferentes a propósito:

- **`ProductMapper` (en `domain.port`)** mapea Commands → `Product`. Solo lo necesita el caso de uso. Su impl se genera por MapStruct en el mismo paquete.
- **`ProductEntityMapper` (en `infrastructure.persistence`)** mapea `Product ↔ ProductEntity`. Es un detalle del adaptador JPA — no es un contrato de dominio, por eso no vive en `domain.port`.
- **`ProductRestMapper` (en `interfaces.rest.mapper`)** mapea `ProductRequest ↔ Command ↔ ProductResponse`. Es el traductor entre HTTP y dominio.

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

`POST /api/products/query` ejecuta una búsqueda filtrada/ordenada/paginada cuyos criterios
viajan en el **cuerpo** de la petición en vez de en query params de la URL, siguiendo la idea
del método HTTP `QUERY` definido en [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html)
("The HTTP QUERY Method"): una petición *safe* (no muta estado) e idempotente, pero con cuerpo
como un POST.

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
interfaces/rest/dto/ProductQueryRequest.java     filtros + page (anidado, @Valid)
shared/rest/dto/PagingRequest.java               number + size + sortBy + sortDirection — agrupados en "page", reutilizable por cualquier feature
interfaces/rest/dto/ProductPageResponse.java     content + page + size + totalElements + totalPages
domain/port/ProductSearchCriteria.java           record de dominio, agnóstico de HTTP/JPA
domain/port/ProductPage.java                     record de dominio, agnóstico de Spring Data
infrastructure/persistence/ProductSpecifications.java   ProductSearchCriteria → Specification<ProductEntity>
```

**Por qué la paginación y el orden van agrupados (`"page": {"number": 0, "size": 20, "sortBy": "price",
"sortDirection": "DESC"}`) y no como campos sueltos.** Mantiene la consulta legible como un único
objeto por concepto (filtros al nivel raíz, todo lo relativo a "qué página, en qué orden" bajo
`page`) y reutiliza el vocabulario `number`/`size` que el propio Spring Data usa para páginas, en
vez de inventar uno nuevo. Requiere `@Valid` en el campo `page` de `ProductQueryRequest` para que
las restricciones de `PagingRequest` (`@PositiveOrZero` en `number`, `@Min`/`@Max` en `size`,
`@Pattern` en `sortDirection`) se validen en cascada — sin esa anotación, Bean Validation no
desciende a los campos de un objeto anidado.

**Por qué `PagingRequest` vive en `com.ej1.app.shared.rest.dto`, no en `product.interfaces.rest.dto`.**
No depende de nada del agregado `Product` — es un objeto puramente de transporte HTTP que cualquier
futuro endpoint de búsqueda paginada puede reutilizar como su propio campo `page`. Vive fuera del
paquete `product` desde el principio para que la próxima entidad no tenga que copiarlo ni importarlo
cruzando features.

**Por qué `sortBy` no lleva ningún `@Pattern`/enum en `PagingRequest`, a pesar de que `number`,
`size` y `sortDirection` sí llevan su propia validación ahí mismo.** El conjunto de nombres de campo
válidos para `sortBy` es conocimiento de `Product` (o de la entidad que sea), no de la paginación en
general — un `PagingRequest` compartido no tiene una lista fija correcta que poner. `number`, `size`
y `sortDirection` sí son universales (cualquier página tiene un tamaño entre 1 y 100; cualquier orden
es `ASC` o `DESC`), así que esos tres sí se validan ahí con Bean Validation normal. `sortBy` viaja sin
restricción hasta `ProductService.search`, que lo valida contra su propio `ALLOWED_SORT_FIELDS` como
regla de negocio — ver más abajo.

**Flujo en `ProductController.query(...)`:**
1. `mapper.toCriteria(request)` — traduce el DTO HTTP a `ProductSearchCriteria`. Los defaults de
   paginación/orden (`page=0`, `size=20`, `sortBy=id`, `sortDirection=ASC`) se resuelven aquí,
   vía `@Mapping(target = "page", source = "page.number", defaultValue = "0")` (y análogo para
   `size`/`sortBy`/`sortDirection`) en `ProductRestMapperAdapter` — **no** como field initializers
   en `ProductQueryRequest`/`PagingRequest`. Jackson deserializa esas clases a través del
   constructor `@AllArgsConstructor` de Lombok cuando lo detecta como creator implícito, y pasa
   `null` explícito para cada propiedad ausente del JSON, lo que salta por completo cualquier
   inicializador de campo Java — de ahí que el default tenga que vivir en el mapper, no en el DTO.
   `source = "page.number"` navega el objeto anidado; MapStruct genera el hop null-safe
   (`request.getPage() == null ? null : request.getPage().getNumber()`) para cuando `page` falta
   por completo del body, cayendo igualmente en el `defaultValue`.
2. `service.search(criteria)` — valida dos reglas de negocio que Bean Validation no puede expresar
   con una anotación de campo: `minPrice > maxPrice` (regla cruzada entre dos campos) y `sortBy`
   no está en `ProductService.ALLOWED_SORT_FIELDS` (conocimiento propio de `Product`, imposible de
   fijar en el `PagingRequest` compartido — ver más arriba). Ambas lanzan `InvalidProductQueryException`
   → 422, y delega en el repositorio si pasan.
3. `ProductRepositoryAdapter.search(criteria)` — construye un `Specification<ProductEntity>` vía
   `ProductSpecifications.from(criteria)` (filtros `LIKE` case-insensitive para
   `name`/`sku`/`category`/`brand`/`tags`, rango para `price`, igualdad para `active`; los filtros
   ausentes no restringen nada) y un `PageRequest` para el orden/paginación, y mapea el
   `Page<ProductEntity>` resultante a `ProductPage` (dominio). `category`/`brand` son `List<String>`
   en vez de `String`: cada valor de la lista se evalúa con el mismo `LIKE` case-insensitive de
   siempre y los resultados se combinan con OR (`containsIgnoreCaseAnyOf` en `ProductSpecifications`),
   de modo que ese grupo OR se AND-ea con el resto de filtros igual que haría un único valor.
4. `mapper.toPageResponse(result)` — proyecta a `ProductPageResponse`, reutilizando
   `toResponse(Product)` elemento a elemento.

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
sus propios parámetros `sortBy`/`sortDirection`, aunque viajen sueltos como query params en vez de
agrupados en un `page` — llegan al mismo `ProductService.search`, así que heredan el mismo 400/422.

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
| `ProductServiceTest`                      | `domain.service`| Mockito (`@Mock` repo + `@Spy` mapper) — 18 tests | Casos de uso, validaciones, mapeos, `search` (delegación + regla `minPrice > maxPrice`) |
| `ProductControllerTest`                   | `interfaces.rest`| `@WebMvcTest` + MockMvc + `ProductRestMapperImpl` real — 26 tests | HTTP flows, validación Bean, `@RestControllerAdvice`, PATCH (200/400/404/409), QUERY (200/400/415/422) |
| `JsonPatchTest`                           | `shared.rest.patch`| JUnit puro, sin Spring — 19 tests | Las 6 operaciones RFC 6902, ejemplos del propio RFC, casos de error, atomicidad |
| `ProductRepositoryAdapterTest`            | `infrastructure.persistence`| `@DataJpaTest` + H2 + `ProductEntityMapperImpl` real — 16 tests | Persistencia + mapeo entity↔domain (incluido update con `id` preservado), `search` (filtros, orden, paginación contra H2 real) |
| `ApplicationTests`                        | global         | `@SpringBootTest` — 1 test | Contexto completo arranca limpio  |

### Notas sobre los tests

- **Controller**: el `ProductRestMapperImpl` se inyecta como bean real (vía `@Import`), no como mock. Mockear el mapper daba respuestas vacías porque `toResponse` devolvía `null`. Mantenerlo real verifica también la serialización JSON y la traducción Request↔Command.
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