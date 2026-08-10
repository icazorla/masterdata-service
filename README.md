# app

Spring Boot 4.1.0 starter project.

## Stack
- Java 17
- Spring Boot 4.1.0
- Spring Web
- Spring Data JPA + Hibernate
- H2 (in-memory)
- Lombok

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
| POST   | `/api/products`            | Create (`@Valid` body, 201 + Location)|
| PUT    | `/api/products/{id}`       | Update (`@Valid` body)                |
| DELETE | `/api/products/{id}`       | Delete (204)                          |

Example payload:
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
