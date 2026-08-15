# app

Spring Boot 4.1.0 starter project.

## Stack
- Java 17
- Spring Boot 4.1.0
- Spring Web
- Spring Data JPA + Hibernate
- H2 (in-memory)
- Lombok
- springdoc-openapi (Swagger UI)

## Requirements
- JDK 17+
- Maven 3.9+

## Run
```bash
mvn spring-boot:run
```
App starts on http://localhost:8080

## H2 Console
http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:appdb`
- User: `sa`
- Password: (empty)

## API Docs (Swagger)
Documentación interactiva generada automáticamente con [springdoc-openapi](https://springdoc.org/) a partir de las anotaciones del controlador (`@Tag`, `@Operation`, `@ApiResponses`) y de los DTOs (`@Schema`).

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

Con la app arrancada (`mvn spring-boot:run`), abre la Swagger UI en el navegador para probar cada endpoint (`GET`/`POST`/`PUT`/`DELETE` de `/api/products`) sin necesidad de Postman ni curl.

## Build
```bash
mvn clean package
```

## Test
```bash
mvn test
```

## Architecture
Layered Architecture (Clean Architecture variant). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for:
- 4 layers: `domain`, `application`, `infrastructure`, `interfaces`
- Dependency rules between layers
- Repository port/adapter pattern
- How to add a new entity

## API
Base path: `/api/products`

| Method | Path                       | Action                                |
|--------|----------------------------|---------------------------------------|
| GET    | `/api/products`            | List all                              |
| GET    | `/api/products/{id}`       | Get by id (404 if not found)          |
| GET    | `/api/products/sku/{sku}`  | Get by sku (404 if not found)         |
| POST   | `/api/products/query`      | Search (filters/sort/paging in body — RFC 10008 semantics) |
| POST   | `/api/products`            | Create (`@Valid` body, 201 + Location)|
| PUT    | `/api/products/{id}`       | Full update (`@Valid` body)           |
| PATCH  | `/api/products/{id}`       | Partial update — JSON Patch, RFC 6902 |
| DELETE | `/api/products/{id}`       | Delete (204)                          |

Example payload (POST/PUT):
```json
{
  "name": "Teclado mecánico",
  "sku": "KB-001",
  "price": 89.99,
  "stock": 10,
  "active": true,
  "category": "Periféricos",
  "brand": "Logitech"
}
```

### QUERY — búsqueda de productos (RFC 10008)
`POST /api/products/query` ejecuta una búsqueda filtrada/ordenada/paginada cuyos criterios viajan en el **cuerpo** de la petición en vez de en query params, siguiendo la semántica del método HTTP `QUERY` definido en [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html). Se expone sobre `POST` porque Spring Framework aún no soporta el verbo `QUERY` de forma nativa (llega en Spring Framework 7.1); ver [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para el detalle de esa decisión.

Todos los filtros son opcionales y se combinan con AND. `category` y `brand` admiten varios
valores: un producto coincide si contiene (parcial, sin distinguir mayúsculas) alguno de ellos (OR).
Paginación **y orden** van agrupados bajo `page` — `number`/`size`/`sortBy`/`sortDirection`, todos
opcionales (por defecto `0`/`20`/`id`/`ASC`):

```bash
curl -X POST http://localhost:8080/api/products/query \
  -H "Content-Type: application/json" \
  -d '{
    "category": ["Periféricos", "Pantallas"],
    "minPrice": 10,
    "maxPrice": 100,
    "active": true,
    "page": { "number": 0, "size": 20, "sortBy": "price", "sortDirection": "DESC" }
  }'
```

Respuesta paginada:
```json
{
  "content": [ { "id": 1, "sku": "KB-001", "price": 89.99, "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Códigos de respuesta:

| Status | Cuándo                                                                                     |
|--------|----------------------------------------------------------------------------------------------|
| 200    | Consulta ejecutada (incluso sin resultados — `content: []`)                                  |
| 400    | Payload inválido: tipo de dato, `size` fuera de 1-100, `sortDirection` distinto de ASC/DESC…  |
| 415    | `Content-Type` no soportado                                                                  |
| 422    | Consulta válida pero inconsistente (p. ej. `minPrice > maxPrice`, o `sortBy` no es un campo ordenable de producto — la lista de campos válidos es una regla de negocio, no de formato) |

`GET /api/products` acepta los mismos cuatro parámetros como query params planos (`page`, `size`,
`sortBy`, `sortDirection`) con idéntica semántica y los mismos códigos de estado (400 para
`sortDirection` inválido, 422 para `sortBy` desconocido).

### PATCH — JSON Patch (RFC 6902)
`PATCH /api/products/{id}` acepta un documento [JSON Patch (RFC 6902)](https://www.rfc-editor.org/rfc/rfc6902) con `Content-Type: application/json-patch+json`. Soporta las seis operaciones del estándar: `add`, `remove`, `replace`, `move`, `copy`, `test`.

```bash
curl -X PATCH http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json-patch+json" \
  -d '[
    { "op": "test", "path": "/sku", "value": "KB-001" },
    { "op": "replace", "path": "/price", "value": 79.99 },
    { "op": "add", "path": "/description", "value": "Oferta de lanzamiento" },
    { "op": "remove", "path": "/tags" }
  ]'
```

Solo son parcheables los campos editables del producto (los mismos del payload de POST/PUT); `id`, `createdAt` y `updatedAt` no existen en el documento, así que cualquier operación sobre ellos falla con 400. El resultado final se valida con las mismas reglas Bean Validation que POST/PUT.

Códigos de respuesta:

| Status | Cuándo                                                              |
|--------|----------------------------------------------------------------------|
| 200    | Patch aplicado, producto actualizado                                 |
| 400    | Documento inválido, operación no aplicable, o resultado inválido     |
| 404    | Producto no encontrado                                               |
| 409    | Una operación `test` falló, o el SKU resultante ya existe            |

La implementación de RFC 6902 (`shared/rest/patch/JsonPatch.java`) está escrita a mano sobre `Map`/`List` genéricos en vez de usar una librería de terceros: Spring Boot 4.1 corre sobre **Jackson 3** (`tools.jackson`), mientras que las librerías `json-patch` publicadas (java-json-tools, zjsonpatch...) están todas construidas contra `JsonNode` de Jackson 2 — añadirlas habría significado cargar un segundo stack de Jackson incompatible solo para esto. Vive en `shared` (no en `product`) porque no depende de nada del agregado `Product`: cualquier feature que exponga un endpoint `PATCH` puede reutilizarla, junto con `JsonPatchApplier` (orquesta conversión + patch + validación) y `JsonPatchExceptionHandler` (traduce sus excepciones a HTTP).
