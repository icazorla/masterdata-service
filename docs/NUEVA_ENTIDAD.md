# Cómo añadir una nueva entidad

Esta guía describe, paso a paso, cómo añadir una nueva entidad al proyecto replicando exactamente el patrón hexagonal usado por la entidad `Product` (`src/main/java/com/ej1/app/product/**`). Sustituye `TuEntidad` / `tuentidad` por el nombre real de tu entidad (p. ej. `Order` / `order`) y ajusta los campos a tu dominio.

## 0. Contexto del proyecto

- **Stack**: Java 17, Spring Boot, Spring Web, Spring Data JPA + Hibernate, H2 en memoria, Lombok, MapStruct, springdoc-openapi (Swagger), Bean Validation (`jakarta.validation`), JSpecify (`@Nullable` / `@NullMarked`).
- **Arquitectura**: hexagonal estricta (Ports & Adapters). El dominio es puro (sin frameworks, salvo `@Service`/`@Transactional` de Spring en el Service). Todo lo externo (REST, JPA, manejo de errores) entra por adaptadores.
- **Base de datos**: `spring.jpa.hibernate.ddl-auto=update` en `application.properties` — Hibernate crea/actualiza las tablas automáticamente a partir de las entidades JPA. No hace falta escribir DDL ni migraciones manuales para una entidad nueva.

### Reglas de dependencia entre capas

| Capa | Puede importar | NO puede importar |
|---|---|---|
| `domain` | `shared.domain` (puro); Lombok; `@Service`/`@Transactional` de Spring solo en el Service | `infrastructure`, `interfaces`, `shared.rest`, Spring MVC/Data, MapStruct |
| `shared.domain` | nada del proyecto (solo Lombok) | todo lo demás |
| `shared.rest` | `shared.domain`, Spring MVC, Jackson, Bean Validation | `domain`/`infrastructure`/`interfaces` de cualquier feature |
| `interfaces` | `domain`, `shared` (domain + rest), Spring MVC | `infrastructure`, Spring Data JPA |
| `infrastructure` | `domain`, `shared` (domain + rest), JPA, Spring | `interfaces` |

Piezas de `shared` que **no hay que tocar** al añadir una entidad (se reutilizan tal cual): `PageResult<T>`, `Paging`, `SortableFieldValidator`, `PageHeaders`, todo `shared/rest/patch/*` (JSON Patch RFC 6902) + `JsonPatchExceptionHandler`, y `OpenApiConfig`.

## 1. Estructura de paquetes a crear

```
src/main/java/com/ej1/app/tuentidad/
├── domain/
│   ├── TuEntidad.java
│   ├── exception/
│   │   ├── TuEntidadNotFoundException.java
│   │   └── (otras excepciones de negocio, p. ej. DuplicateXxxException)
│   ├── port/
│   │   ├── CreateTuEntidadCommand.java
│   │   ├── UpdateTuEntidadCommand.java
│   │   ├── TuEntidadMapper.java
│   │   ├── TuEntidadRepository.java
│   │   └── TuEntidadSearchCriteria.java
│   └── service/
│       └── TuEntidadService.java
├── infrastructure/
│   ├── exception/
│   │   └── ApiExceptionHandler.java
│   ├── persistence/
│   │   ├── TuEntidadEntity.java
│   │   ├── TuEntidadEntityMapper.java
│   │   ├── TuEntidadJpaRepository.java
│   │   ├── TuEntidadMapperAdapter.java
│   │   ├── TuEntidadRepositoryAdapter.java
│   │   └── TuEntidadSpecifications.java
│   └── rest/
│       └── TuEntidadRestMapperAdapter.java
└── interfaces/
    └── rest/
        ├── TuEntidadController.java
        ├── dto/
        │   ├── TuEntidadQueryRequest.java
        │   ├── TuEntidadRequest.java
        │   ├── TuEntidadResponse.java
        │   └── package-info.java
        └── mapper/
            └── TuEntidadRestMapper.java
```

## 2. Capa `domain`

### 2.1 `domain/TuEntidad.java`
Clase `final`, inmutable, con `@Getter` de Lombok (sin setters):
- Campos propios de la entidad (id, timestamps y campos de negocio).
- Constructor **privado** que recibe un `Builder` interno.
- `Builder` interno **escrito a mano** (no `@Builder` de Lombok) con métodos fluidos por campo y `build()`.
- Método estático `builder()`.
- `toBuilder()`: crea un `Builder` prellenado con los valores actuales — lo usa `TuEntidadMapper.applyUpdate` para construir la versión actualizada preservando lo no modificado.
- (Opcional) `withIdAndTimestamps(...)`: crea una copia con nuevo id/timestamps.

No debe importar nada de `infrastructure` ni `interfaces`.

### 2.2 `domain/exception/`
- `TuEntidadNotFoundException extends RuntimeException`: al menos un constructor `(Long id)` con mensaje tipo `"TuEntidad not found: id=..."`. Se lanza en el Service para 404.
- Excepciones de negocio adicionales según aplique, por ejemplo `DuplicateXxxException` (409) para restricciones de unicidad, o `InvalidTuEntidadQueryException` (422) para reglas de búsqueda que Bean Validation no puede expresar (validaciones cross-field, `sortBy` no permitido, etc.). Sigue la distinción RFC: 400 = formato inválido (Bean Validation), 422 = contenido válido pero semánticamente inconsistente.

### 2.3 `domain/port/CreateTuEntidadCommand.java` y `UpdateTuEntidadCommand.java`
`record`s independientes (aunque tengan el mismo shape) con los campos editables de la entidad (sin `id`/timestamps). Se mantienen separados a propósito para que la firma del caso de uso sea explícita y pueda evolucionar de forma independiente.

### 2.4 `domain/port/TuEntidadMapper.java`
Interfaz de puerto de dominio **sin anotaciones de Spring/MapStruct**:
- `TuEntidad toDomain(CreateTuEntidadCommand cmd)`
- `TuEntidad toDomain(UpdateTuEntidadCommand cmd)`
- `default TuEntidad applyUpdate(TuEntidad existing, UpdateTuEntidadCommand cmd)`: implementado aquí mismo con `existing.toBuilder()...build()`, respetando la regla de "si el campo del comando viene `null`, conserva el valor existente" donde tenga sentido (p. ej. flags booleanos).

Se implementa vía `infrastructure/persistence/TuEntidadMapperAdapter.java` (adapter vacío con `@Mapper`).

### 2.5 `domain/port/TuEntidadRepository.java`
Puerto saliente de persistencia:
- `findById(Long)`, `save(TuEntidad)`, `deleteById(Long)`, `existsById(Long)`.
- Métodos específicos de negocio si aplica (p. ej. `findBySku`, `existsBySku`).
- `search(TuEntidadSearchCriteria criteria, Paging paging) : PageResult<TuEntidad>` — reutiliza `PageResult`/`Paging` de `shared.domain`.

### 2.6 `domain/port/TuEntidadSearchCriteria.java`
`record` con **solo filtros** (sin paginación — eso va aparte en `Paging`). Semántica recomendada, igual que en `Product`:
- Campos de texto (`String`): contiene, case-insensitive.
- Campos de listas (`List<String>`): valores en OR entre sí, y en AND con el resto de filtros.
- Rangos numéricos (`minX`/`maxX`) y flags (`Boolean`): coincidencia exacta.

### 2.7 `domain/service/TuEntidadService.java`
`@Service @RequiredArgsConstructor`, inyecta `TuEntidadRepository repository` y `TuEntidadMapper mapper` por constructor (Lombok).
- `static final Set<String> ALLOWED_SORT_FIELDS` — **package-private** (no `private`), con los nombres de campo por los que se puede ordenar. Debe corresponder exactamente a getters reales de `TuEntidad` (hay un test dedicado que lo valida por reflexión, ver sección 5).
- `findById(Long)` → `@Transactional(readOnly = true)`, `repository.findById(id).orElseThrow(TuEntidadNotFoundException::new)`.
- `create(CreateTuEntidadCommand cmd)` → `@Transactional`, valida restricciones de negocio (duplicados, etc.), `mapper.toDomain(cmd)` → `repository.save(...)`.
- `update(Long id, UpdateTuEntidadCommand cmd)` → `@Transactional`, busca existente (404 si no existe), valida duplicados si cambia algún campo único, `mapper.applyUpdate(existing, cmd)` → `repository.save(...)`.
- `search(TuEntidadSearchCriteria criteria, Paging paging)` → `@Transactional(readOnly = true)`, valida reglas cruzadas (p. ej. `minX > maxX` → excepción 422), valida `sortBy` con `SortableFieldValidator.requireSortable(paging.sortBy(), ALLOWED_SORT_FIELDS, InvalidTuEntidadQueryException::new)`, delega en `repository.search(...)`.
- `delete(Long id)` → `@Transactional`, `existsById` (404 si no existe) → `repository.deleteById(id)`.

## 3. Capa `infrastructure`

### 3.1 `infrastructure/persistence/TuEntidadEntity.java`
`@Entity @Table(name = "tuentidades") @Data @NoArgsConstructor @AllArgsConstructor @Builder` (aquí sí Lombok `@Builder`, a diferencia del dominio).
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`.
- Resto de campos con `@Column(nullable = ..., length = ..., precision = ..., scale = ..., unique = ..., name = "...")` según el campo.
- `createdAt` → `@CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)`.
- `updatedAt` → `@UpdateTimestamp @Column(name = "updated_at", nullable = false)`.

### 3.2 `infrastructure/persistence/TuEntidadEntityMapper.java`
`@Mapper(componentModel = "spring")` interfaz (MapStruct genera `TuEntidadEntityMapperImpl` en el mismo paquete):
- `TuEntidad toDomain(TuEntidadEntity entity)`.
- `@Mapping(target = "id", source = "id") @Mapping(target = "createdAt", ignore = true) @Mapping(target = "updatedAt", ignore = true) TuEntidadEntity toEntity(TuEntidad domain)` — los `ignore = true` dejan que Hibernate rellene los timestamps; mapear `id` explícitamente es clave para que `save()` haga `merge` en vez de `insert` al actualizar.

### 3.3 `infrastructure/persistence/TuEntidadJpaRepository.java`
```java
@Repository
public interface TuEntidadJpaRepository
        extends JpaRepository<TuEntidadEntity, Long>, JpaSpecificationExecutor<TuEntidadEntity> {
    // métodos derivados específicos si aplica, p. ej.:
    // Optional<TuEntidadEntity> findByCodigo(String codigo);
    // boolean existsByCodigo(String codigo);
}
```

### 3.4 `infrastructure/persistence/TuEntidadMapperAdapter.java`
```java
@Mapper(componentModel = "spring")
public interface TuEntidadMapperAdapter extends TuEntidadMapper {
}
```
Interfaz vacía: solo añade la anotación MapStruct al puerto de dominio, manteniendo `TuEntidadMapper` libre de dependencias de infraestructura.

### 3.5 `infrastructure/persistence/TuEntidadRepositoryAdapter.java`
`@Repository @RequiredArgsConstructor implements TuEntidadRepository`, inyecta `TuEntidadJpaRepository jpa` y `TuEntidadEntityMapper mapper`. Implementa todos los métodos del puerto delegando en `jpa` + `mapper::toDomain`/`toEntity`.
`search(criteria, paging)`:
```java
Sort sort = Sort.by(Sort.Direction.fromString(paging.sortDirection()), paging.sortBy());
Pageable pageable = PageRequest.of(paging.page(), paging.size(), sort);
Page<TuEntidadEntity> page = jpa.findAll(TuEntidadSpecifications.from(criteria), pageable);
// mapear Page<TuEntidadEntity> a PageResult<TuEntidad> (content/number/size/totalElements/totalPages)
```

### 3.6 `infrastructure/persistence/TuEntidadSpecifications.java`
Clase `final` con constructor privado, **package-private** (no pública), solo usada dentro de `infrastructure.persistence`:
- `static Specification<TuEntidadEntity> from(TuEntidadSearchCriteria criteria)`: combina un `Stream` de specs opcionales (una por filtro no nulo), filtra los `null`, hace `reduce(Specification::and)`, con fallback `(root, query, cb) -> cb.conjunction()` si no hay ningún filtro. **Importante**: no usar `Specification.where(null).and(...)` porque `SimpleJpaRepository.findAll` rechaza una `Specification` `null` explícita.
- Métodos privados auxiliares: `containsIgnoreCase(field, value)`, `containsIgnoreCaseAnyOf(field, values)` (OR entre valores de una lista), y uno por cada filtro de rango/flag (`minX`, `maxX`, `activo`, etc.).

### 3.7 `infrastructure/rest/TuEntidadRestMapperAdapter.java`
```java
@Mapper(componentModel = "spring")
public interface TuEntidadRestMapperAdapter extends TuEntidadRestMapper {
}
```
Mismo patrón que `TuEntidadMapperAdapter`, pero para el mapper REST; vive en `infrastructure.rest` (no en `interfaces.rest`) para mantener el puerto REST libre de MapStruct.

### 3.8 `infrastructure/exception/ApiExceptionHandler.java`
`@RestControllerAdvice @Order(Ordered.LOWEST_PRECEDENCE) @Slf4j`. El `@Order` es importante: debe quedar **el último**, porque su handler catch-all de `Exception` no debe interceptar excepciones destinadas a otros advices (p. ej. `JsonPatchExceptionHandler` de `shared.rest.exception`, que usa `@Order(0)`).
- Handlers específicos de la entidad: `TuEntidadNotFoundException` → 404, excepciones de duplicado → 409, excepciones de query inválida → 422.
- Handlers genéricos reutilizables (si no existe ya uno global para toda la app, replicar tal cual): `MethodArgumentNotValidException` → 400, `ConstraintViolationException` → 400, `HttpMessageNotReadableException` → 400, `HttpMediaTypeNotSupportedException` → 415, `Exception` → 500 (fallback).
- `build(HttpStatus status, String message, List<String> errors)`: construye el body `{timestamp, status, error, message, errors?}`.

> Nota: si el proyecto ya tiene un `ApiExceptionHandler` que cubre los casos genéricos (como el de `product`), valora si conviene consolidar los handlers genéricos en uno solo compartido en vez de duplicarlos por cada entidad.

## 4. Capa `interfaces`

### 4.1 `interfaces/rest/dto/package-info.java`
```java
@NullMarked
package com.ej1.app.tuentidad.interfaces.rest.dto;

import org.jspecify.annotations.NullMarked;
```
Marca el paquete DTO como null-safe por defecto (JSpecify): cualquier campo que pueda ser `null` debe anotarse explícitamente `@Nullable`.

### 4.2 `interfaces/rest/dto/TuEntidadRequest.java`
`@Data @NoArgsConstructor @AllArgsConstructor @Builder @Schema(...)`. Payload de creación/actualización con todos los campos editables y validaciones Bean Validation según el tipo/obligatoriedad de cada campo (`@NotBlank`, `@NotNull`, `@Size`, `@Positive`, `@Min`, `@Digits`, etc.), y `@Schema(description=..., example=...)` para Swagger. Campos opcionales marcados `@Nullable` (JSpecify).

### 4.3 `interfaces/rest/dto/TuEntidadQueryRequest.java`
`@Data @NoArgsConstructor @AllArgsConstructor @Builder @Schema(...)`. Solo los campos de filtro (mismo shape que `TuEntidadSearchCriteria`), con validaciones/documentación por campo. Las listas de valores (`List<String>`) validan cada elemento, p. ej. `List<@Size(max = 80) String>`.

### 4.4 `interfaces/rest/dto/TuEntidadResponse.java`
`record` con todos los campos de salida (incluye `id`, timestamps), campos opcionales marcados `@Nullable`.

### 4.5 `interfaces/rest/mapper/TuEntidadRestMapper.java`
Interfaz de puerto **sin anotaciones** MapStruct/Spring (implementada por `TuEntidadRestMapperAdapter` en infraestructura):
- `toResponse(TuEntidad)` y `toResponseList(List<TuEntidad>)` (reusa `toResponse` elemento a elemento).
- `toCreateCommand(TuEntidadRequest)`, `toUpdateCommand(TuEntidadRequest)`.
- `toRequest(TuEntidad)`: proyección editable de la entidad (excluye id/timestamps), usada como base para aplicar un JSON Patch.
- `toCriteria(TuEntidadQueryRequest)`: mapeo campo a campo (sin `@Mapping` manual si los nombres coinciden).

### 4.6 `interfaces/rest/TuEntidadController.java`
`@RestController @RequestMapping("/api/tuentidades") @RequiredArgsConstructor @Validated @Tag(name = "TuEntidades", ...)`. Inyecta `TuEntidadService service`, `TuEntidadRestMapper mapper`, y `JsonPatchApplier jsonPatchApplier` (de `shared.rest.patch`) si hay endpoint PATCH.

Endpoints recomendados (calcados de `ProductController`):
- `GET /` (`findAll`): parámetros de paginación `page`, `size`, `sortBy`, `sortDirection` con Bean Validation (`@PositiveOrZero`, `@Min`, `@Max`, `@Pattern(regexp = "ASC|DESC")`); construye un `TuEntidadSearchCriteria` vacío; llama a `service.search`; responde `ResponseEntity.ok().headers(PageHeaders.of(result)).body(mapper.toResponseList(result.content()))`.
- `GET /{id}` (`findById`).
- `POST /query` (`query`): mismos parámetros de paginación + `@Valid @RequestBody TuEntidadQueryRequest`; `mapper.toCriteria(request)` → `service.search` → respuesta con cabecera extra `"Accept-Query": "application/json"` + `PageHeaders`.
- `POST /` (`create`): `@Valid @RequestBody TuEntidadRequest`; construye `Location` con `ServletUriComponentsBuilder`; devuelve 201.
- `PUT /{id}` (`update`).
- `PATCH /{id}` (`consumes = "application/json-patch+json"`, `patch`): obtiene la entidad existente → `mapper.toRequest(existing)` → `jsonPatchApplier.apply(current, patch)` → `service.update(id, mapper.toUpdateCommand(patchedRequest))`.
- `DELETE /{id}` (`delete`): 204.

Documenta cada endpoint con `@Operation`/`@ApiResponses`/`@Parameter`/`@Schema` de springdoc para que aparezca correctamente en Swagger (`/swagger-ui.html`).

No es necesario tocar `OpenApiConfig` — es configuración global no específica de una entidad.

## 5. Tests a crear

| Test | Ubicación | Qué cubre |
|---|---|---|
| `TuEntidadServiceTest` | `src/test/java/.../tuentidad/domain/service/` | Mockito (`@Mock` del repository + `@Spy` del mapper real vía `Mappers.getMapper(...)`): casos de uso (create/update/find/delete), validaciones de negocio, mapeos Command↔Entidad, `search` (delegación + reglas de validación cruzada y `sortBy` inválido → 422). |
| `TuEntidadServiceSortFieldsTest` | `src/test/java/.../tuentidad/domain/service/` | JUnit puro + reflexión: verifica que cada nombre declarado en `ALLOWED_SORT_FIELDS` corresponde a un getter real de la entidad de dominio (evita desincronización silenciosa tras un rename de campo). |
| `TuEntidadControllerTest` | `src/test/java/.../tuentidad/interfaces/rest/` | `@WebMvcTest` + MockMvc, importando el mapper REST **real** generado por MapStruct (`TuEntidadRestMapperAdapterImpl`, no un mock — mockearlo produce respuestas vacías): flujos HTTP completos, validación Bean, manejo de excepciones vía `@RestControllerAdvice`, PATCH (200/400/404/409 según aplique), GET paginado (200/400/422), POST /query (200/400/415/422). |
| `TuEntidadRepositoryAdapterTest` | `src/test/java/.../tuentidad/infrastructure/persistence/` | `@DataJpaTest` + H2 real, importando el mapper de persistencia **real** (`TuEntidadEntityMapperImpl`): persistencia y mapeo entity↔domain (incluida la preservación de `id` en updates), `search` (filtros/orden/paginación contra H2 real). |

No hace falta replicar `JsonPatchTest` (genérico, ya cubre RFC 6902 a nivel de `shared`) ni `ApplicationTests` (arranque de contexto completo, se beneficia automáticamente de cualquier entidad bien cableada).

## 6. Checklist resumido

1. `domain/TuEntidad.java` — POJO inmutable, builder manual.
2. `domain/exception/...` — `TuEntidadNotFoundException` + las que apliquen.
3. `domain/port/TuEntidadRepository.java`, `TuEntidadSearchCriteria.java`.
4. `domain/port/CreateTuEntidadCommand.java`, `UpdateTuEntidadCommand.java` — records.
5. `domain/port/TuEntidadMapper.java` — interfaz sin anotaciones, con `toDomain(Command)` y `applyUpdate(...)`.
6. `domain/service/TuEntidadService.java` — `@Service @RequiredArgsConstructor`, `ALLOWED_SORT_FIELDS` propio, usa `SortableFieldValidator`.
7. `interfaces/rest/dto/TuEntidadRequest.java`, `TuEntidadResponse.java`, `TuEntidadQueryRequest.java` + `package-info.java` con `@NullMarked`.
8. `interfaces/rest/mapper/TuEntidadRestMapper.java` — interfaz de puerto sin anotaciones.
9. `infrastructure/persistence/TuEntidadEntity.java`, `TuEntidadJpaRepository.java`, `TuEntidadRepositoryAdapter.java`, `TuEntidadEntityMapper.java` (`@Mapper`), `TuEntidadSpecifications.java`, `TuEntidadMapperAdapter.java` (vacío + `@Mapper`).
10. `infrastructure/rest/TuEntidadRestMapperAdapter.java` — vacío + `@Mapper`.
11. `interfaces/rest/TuEntidadController.java` — inyecta Service, RestMapper, y `JsonPatchApplier` si hay PATCH.
12. `infrastructure/exception/ApiExceptionHandler.java` propio (o extender el existente) — `@RestControllerAdvice @Order(LOWEST_PRECEDENCE)`.
13. Tests: `TuEntidadServiceTest`, `TuEntidadServiceSortFieldsTest`, `TuEntidadControllerTest`, `TuEntidadRepositoryAdapterTest`.

No es necesario tocar `shared.domain`, `shared.rest` (incluido `patch`), ni `OpenApiConfig` — se reutilizan tal cual para cualquier entidad nueva.
