# Architecture

Este proyecto sigue una **Arquitectura Hexagonal estricta (Ports & Adapters)**. El núcleo es el dominio puro (sin Spring ni JPA), y todo lo externo (REST, JPA, manejo de errores) entra a través de adaptadores que implementan los puertos definidos por el dominio.

## Capas

```
+--------------------------------------------------------------+
|  interfaces/rest      ProductController  (HTTP entrante)     |
|                       dto/        ProductRequest, Response   |
|                       mapper/     ProductRestMapper          |
+--------------------------------------------------------------+
                          |
                          |  ProductRequest → Command
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
|  +-----------------+                                          |
|  | exception/      |   ProductNotFoundException               |
|  |                 |   DuplicateSkuException                  |
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
|  +-----------------------+                                    |
|  | exception/            |  ApiExceptionHandler                |
|  |                       |   (@RestControllerAdvice)          |
|  +-----------------------+                                    |
+--------------------------------------------------------------+
```

## Reglas de dependencia

| Capa              | Puede importar                              | NO puede importar                                  |
|-------------------|---------------------------------------------|----------------------------------------------------|
| `domain`          | nada del proyecto (solo `lombok`)           | `infrastructure`, `interfaces`, `org.springframework.*`, `org.mapstruct.*` |
| `interfaces`      | `domain`, Spring MVC                        | `infrastructure`, `org.springframework.data.jpa.*` |
| `infrastructure`  | `domain`, JPA, Spring                       | `interfaces`                                       |

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
| `MethodArgumentNotValidException` | 400 | Bean Validation falla en `ProductRequest`   |
| Cualquier otra            | 500  | Fallback                                          |

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

## Tests por capa

| Test                                      | Capa           | Tecnología                | Cobertura                         |
|-------------------------------------------|----------------|---------------------------|-----------------------------------|
| `ProductServiceTest`                      | `domain.service`| Mockito (`@Mock` repo + `@Spy` mapper) — 15 tests | Casos de uso, validaciones, mapeos |
| `ProductControllerTest`                   | `interfaces.rest`| `@WebMvcTest` + MockMvc + `ProductRestMapperImpl` real — 12 tests | HTTP flows, validación Bean, `@RestControllerAdvice` |
| `ProductRepositoryAdapterTest`            | `infrastructure.persistence`| `@DataJpaTest` + H2 + `ProductEntityMapperImpl` real — 11 tests | Persistencia + mapeo entity↔domain (incluido update con `id` preservado) |
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