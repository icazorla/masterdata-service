# app

API REST (Spring Boot 4.1) para la **gestión genérica de tablas de catálogo** (datos maestros como marcas o categorías), con traducciones por idioma, búsqueda, ordenación y actualización parcial vía JSON Patch. Arquitectura hexagonal.

## Índice
- [Características funcionales](#características-funcionales)
  - [Gestión genérica de catálogos](#1-gestión-genérica-de-catálogos)
  - [Operaciones disponibles](#2-operaciones-disponibles)
  - [Consultas: filtrado, ordenación y paginación](#3-consultas-filtrado-ordenación-y-paginación)
  - [Multi-idioma (i18n)](#4-multi-idioma-i18n)
  - [Campos adicionales por tipo](#5-campos-adicionales-por-tipo)
  - [Actualización parcial con JSON Patch](#6-actualización-parcial-con-json-patch-rfc-6902)
  - [Validaciones y reglas de negocio](#7-validaciones-y-reglas-de-negocio)
  - [Errores](#8-errores)
  - [Caché](#9-caché)
  - [Añadir un nuevo tipo de catálogo](#10-añadir-un-nuevo-tipo-de-catálogo)
  - [Seguridad](#11-seguridad)
- [Stack](#stack) · [Base de datos](#base-de-datos) · [Run](#run) · [Swagger](#api-docs-swagger) · [Build y tests](#build-y-tests) · [Arquitectura](#arquitectura)

---

## Características funcionales

### 1. Gestión genérica de catálogos

Un **único conjunto de endpoints** gestiona cualquier tabla de catálogo. El tipo de catálogo es un segmento de la ruta, y hay **dos APIs separadas** por prefijo:

```
/api/catalogs/{type}          API de consulta  — solo lectura, localizada (front, otros servicios)
/admin/api/catalogs/{type}    API de gestión   — CRUD completo, con todas las traducciones (backoffice)
```

| | Consulta (`/api/...`) | Gestión (`/admin/api/...`) |
|---|---|---|
| Métodos | solo `GET` (el resto → **405**) | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| `name`/`description` | uno solo, en el idioma de `Accept-Language` | **todas** las traducciones (`translations.<locale>`) |
| Listado | sin paginar por defecto (`paged=false`) | paginado por defecto (`paged=true`) |

Separarlas por prefijo permite proteger, cachear o enrutar la de gestión con una sola regla (`/admin/**`).

| `{type}`   | Tablas                              | Campos adicionales |
|------------|-------------------------------------|--------------------|
| `brand`    | `brands` + `brand_translations`     | `website`          |
| `category` | `categories` + `category_translations` | —               |

- `{type}` no distingue mayúsculas (`/api/catalogs/Brand` = `/api/catalogs/brand`).
- Un tipo no configurado (p. ej. `/api/catalogs/widget`) devuelve **400**.
- Los tipos se declaran en `application.yml` (`app.catalog.types`); añadir uno nuevo **no requiere código** de controller, servicio ni repositorio (ver [punto 10](#10-añadir-un-nuevo-tipo-de-catálogo)).

Cada entrada de catálogo tiene:

| Campo         | Tipo       | Traducido | Descripción |
|---------------|------------|-----------|-------------|
| `id`          | número     | —         | Autogenerado |
| `type`        | texto      | —         | Tipo de catálogo (`brand`, `category`…) |
| `name`        | texto      | sí        | Nombre (máx. 80), único por tipo e idioma |
| `description` | texto      | sí        | Descripción opcional (máx. 500) |
| `weight`      | entero     | no        | Peso para ordenación manual (p. ej. destacar entradas). Por defecto `0` |
| `enabled`     | booleano   | no        | Si la entrada está activa. Por defecto `true`. Las desactivadas no salen en los listados de la API de consulta, pero siguen resolviéndose por id |
| campos extra  | según tipo | no        | Propios de cada tipo (p. ej. `website` en `brand`) |
| `createdAt` / `updatedAt` | fecha-hora | — | Gestionados automáticamente |

### 2. Operaciones disponibles

**API de consulta**

| Método | Ruta                          | Operación | Respuesta OK |
|--------|-------------------------------|-----------|--------------|
| GET    | `/api/catalogs/{type}`        | Listar entradas, con filtro y ordenación (en el idioma del cliente) | 200 + lista |
| GET    | `/api/catalogs/{type}/{id}`   | Obtener una entrada (en el idioma del cliente) | 200 |

**API de gestión**

| Método | Ruta                                | Operación | Respuesta OK |
|--------|-------------------------------------|-----------|--------------|
| GET    | `/admin/api/catalogs`               | Listar los tipos de catálogo y los campos de cada uno | 200 + lista |
| GET    | `/admin/api/catalogs/{type}`        | Listar entradas con **todas** sus traducciones (paginado por defecto) | 200 + lista |
| GET    | `/admin/api/catalogs/{type}/{id}`   | Obtener una entrada con **todas** sus traducciones | 200 |
| POST   | `/admin/api/catalogs/{type}`        | Crear una entrada | 201 + cabecera `Location` |
| PUT    | `/admin/api/catalogs/{type}/{id}`   | Reemplazar una entrada completa | 200 |
| PATCH  | `/admin/api/catalogs/{type}/{id}`   | Modificación parcial (JSON Patch, RFC 6902) | 200 |
| DELETE | `/admin/api/catalogs/{type}/{id}`   | Eliminar (desactivar) una entrada | 204 |

**Tipos de catálogo y sus campos** (`GET /admin/api/catalogs`, nombre del tipo según `Accept-Language`):

```json
[
  {
    "type": "brand",
    "name": "Marca",
    "fields": [
      { "name": "id",          "dataType": "integer",   "required": false, "readOnly": true,  "translatable": false, "sortable": false, "extra": false },
      { "name": "name",        "dataType": "string",    "required": true,  "readOnly": false, "translatable": true,  "sortable": true,  "extra": false },
      { "name": "description", "dataType": "string",    "required": false, "readOnly": false, "translatable": true,  "sortable": true,  "extra": false },
      { "name": "weight",      "dataType": "integer",   "required": false, "readOnly": false, "translatable": false, "sortable": true,  "extra": false },
      { "name": "enabled",     "dataType": "boolean",   "required": false, "readOnly": false, "translatable": false, "sortable": false, "extra": false },
      { "name": "createdAt",   "dataType": "date-time", "required": false, "readOnly": true,  "translatable": false, "sortable": false, "extra": false },
      { "name": "updatedAt",   "dataType": "date-time", "required": false, "readOnly": true,  "translatable": false, "sortable": false, "extra": false },
      { "name": "website",     "dataType": "string",    "required": false, "readOnly": false, "translatable": false, "sortable": true,  "extra": true }
    ]
  },
  { "type": "category", "name": "Categoría", "fields": [ "...solo los campos comunes..." ] }
]
```

- Los tipos salen de `app.catalog.types`, en el orden de `application.yml`.
- `extra: true` marca los campos propios del tipo (las columnas declaradas en su entidad); su `dataType` se deduce del tipo Java de la columna.
- `translatable: true` indica que en el payload el campo va dentro de `translations.<locale>`.

**Crear / reemplazar (POST / PUT)** — payload:

```json
{
  "weight": 10,
  "enabled": true,
  "website": "https://www.logitech.com",
  "translations": {
    "en": { "name": "Logitech", "description": "Peripheral manufacturer" },
    "es": { "name": "Logitech", "description": "Fabricante de periféricos" }
  }
}
```

- `translations` es obligatorio; clave = idioma, y debe incluir siempre `en` (idioma por defecto).
- `weight` es opcional (por defecto `0`).
- `enabled` es opcional (por defecto `true`). Desactivar una entrada es la alternativa a borrarla cuando otros datos la siguen referenciando.
- Los campos extra van como atributos de primer nivel.
- PUT **reemplaza todo**: un idioma o campo extra que no se envíe se elimina.

**Respuesta de la API de consulta** (en el idioma pedido con `Accept-Language`):

```json
{
  "id": 1,
  "name": "Logitech",
  "description": "Fabricante de periféricos",
  "enabled": true,
  "website": "https://www.logitech.com"
}
```

- Sin `type` (ya va en la ruta), `weight` (el orden ya viene aplicado) ni `createdAt`/`updatedAt` (auditoría): solo los expone la API de gestión.
- `enabled` en el listado es siempre `true`; por id puede ser `false` (ver [punto 3](#3-consultas-filtrado-ordenación-y-paginación)).

**Respuesta de la API de gestión** (`GET`/`POST`/`PUT`/`PATCH` bajo `/admin/api/...`): todas las traducciones, con la misma forma que el payload de POST/PUT:

```json
{
  "id": 1,
  "type": "brand",
  "weight": 10,
  "enabled": true,
  "translations": {
    "en": { "name": "Logitech", "description": "Peripheral manufacturer" },
    "es": { "name": "Logitech", "description": "Fabricante de periféricos" }
  },
  "website": "https://www.logitech.com",
  "createdAt": "2026-10-01T10:00:00",
  "updatedAt": "2026-10-01T10:00:00"
}
```

**Eliminar** es un **borrado lógico**: pone `enabled=false` en vez de borrar la fila, así que los datos que referencian la entrada la siguen resolviendo por id. Es idempotente (borrar una entrada ya desactivada devuelve 204 sin cambiar nada) y devuelve 404 si el id no existe. Para recuperarla, PUT/PATCH con `enabled=true`; su nombre sigue ocupado mientras tanto, así que no se puede crear otra entrada con el mismo nombre.

### 3. Consultas: filtrado, ordenación y paginación

`GET /api/catalogs/{type}` (y `GET /admin/api/catalogs/{type}`) admite estos query params, todos opcionales:

| Parámetro       | Valor por defecto | Descripción |
|-----------------|-------------------|-------------|
| `filter`        | —                 | Condiciones de filtrado (ver abajo) |
| `sortBy`        | `name`            | Campo de ordenación |
| `sortDirection` | `ASC`             | `ASC` o `DESC` |
| `paged`         | `false` (consulta) / `true` (gestión) | `true` para paginar el resultado |
| `page`          | `0`               | Página solicitada (0-based); solo con `paged=true` |
| `size`          | `20`              | Tamaño de página (1–100); solo con `paged=true` |

**Filtrado (`filter`)**

Todos los filtros van en un único parámetro `filter`, como condiciones `campo:valor` separadas por `;`. **Todas se combinan con AND**.

```
filter=name:logi;website:.com,.es;price.gte:10;price.lt:50
```

| Forma | Ejemplo | Significado |
|---|---|---|
| `campo:valor` | `name:logi` | Texto: contiene, sin distinguir mayúsculas · otros tipos: igualdad |
| `campo:v1,v2` | `colorCode:#FFF,#000` | Cualquiera de ellos — texto: contiene alguno · otros tipos: `IN` |
| `campo.op:valor` (`gt`, `gte`, `lt`, `lte`) | `price.gte:10` | Rango, solo números y fechas (ISO-8601, p. ej. `2026-10-13`); un único valor |

Campos filtrables:

| Campo | Tipo | Notas |
|---|---|---|
| `name`, `description` | texto | Sobre el texto **en el idioma del cliente** (con fallback a `en`): filtra exactamente lo que el cliente ve |
| `enabled` | booleano | **Solo gestión** (`enabled:false` → solo desactivadas; sin indicar, todas). La API de consulta lista siempre solo activas y responde **422** si se filtra por `enabled` |
| campos adicionales | según la columna | Solo los del tipo consultado (p. ej. `website` en `brand`) |

- `\;`, `\,` y `\\` son un `;`, una `,` y una `\` literales. Los `:` dentro del valor no necesitan escape (solo cuenta el primero), así que valen URLs y fechas con hora.
- En la URL hay que codificar los caracteres especiales, p. ej. `#` → `%23` (Swagger UI lo hace solo).
- Los operadores de cada campo se publican en `filterOperators` de `GET /admin/api/catalog-types` (`contains` para texto, `eq` para booleanos, `eq`/`gt`/`gte`/`lt`/`lte` para números y fechas).
- Las condiciones y los valores vacíos se ignoran; sin `filter` devuelve todas las entradas del tipo (en la API de consulta, todas las activas).
- `GET /api/catalogs/{type}/{id}` devuelve también las entradas desactivadas, para poder mostrar datos que aún las referencian.
- **422** si una condición no tiene la forma `campo:valor`, si el campo no es filtrable para ese tipo (p. ej. `website` en `category`), si el sufijo no es un operador, si se usa un rango sobre texto o booleano, si un rango trae varios valores, o si el valor no encaja con el tipo del campo (`price:abc`, `enabled:quizá`).
- Un tipo no puede declarar un campo adicional llamado `name`, `description` o `enabled`: la aplicación no arranca.

**Ordenación**

| `sortBy`      | Ordena por | Depende del idioma |
|---------------|------------|--------------------|
| `name`        | Nombre (alfabético) | sí — el nombre en el idioma del cliente |
| `description` | Descripción (alfabético) | sí |
| `weight`      | Peso manual (numérico) | no |
| campo extra   | p. ej. `website`, **solo** para el tipo que lo define (`brand`) | no |

- Dirección ascendente (`ASC`) o descendente (`DESC`).
- `sortDirection` con otro valor → **400**; `sortBy` no válido para ese tipo (p. ej. `website` en `category`) → **422**.
- Filtrado y ordenación se ejecutan en base de datos.
- **`weight` manda siempre primero**, descendente (peso alto destaca delante), sin importar `sortBy`/`sortDirection`: el orden real es *weight DESC, luego `sortBy` en la dirección pedida*. Si `sortBy=weight`, no hay un criterio distinto que añadir después, así que en ese caso sí se respeta `sortDirection` directamente sobre `weight` (p. ej. `sortBy=weight&sortDirection=ASC` → peso de menor a mayor). `id` desempata al final para que el orden sea estable entre páginas.

**Paginación**
- **En la API de consulta, por defecto (`paged=false`) no se pagina**: devuelve todas las entradas que cumplen el filtro en una lista JSON plana — lo habitual para poblar selectores y filtros. `page`/`size` se ignoran y no se envían cabeceras de página.
- **En la API de gestión se pagina por defecto** (`paged=true`, `page=0`, `size=20`); `paged=false` devuelve la lista completa.
- **Con `paged=true`** devuelve solo la página `page` de tamaño `size`, con el mismo filtro y orden. El cuerpo sigue siendo una lista JSON plana; los metadatos viajan en las cabeceras `X-Total-Count`, `X-Page-Number`, `X-Page-Size` y `X-Total-Pages`.
- Una página más allá del final devuelve `[]` (con el total real en las cabeceras).
- `page` negativo o `size` fuera de 1–100 → **400** (se validan aunque `paged=false`).
- Las entradas con el mismo valor de `sortBy` se desempatan por `id`, así que el orden es estable entre páginas.

**Ejemplos**

```bash
# Marcas cuyo nombre contiene "logi" y cuya descripción contiene "peri", en español, de mayor a menor peso
curl "http://localhost:8080/api/catalogs/brand?filter=name:logi;description:peri&sortBy=weight&sortDirection=DESC" \
  -H "Accept-Language: es"

# Categorías ordenadas alfabéticamente (Z→A) — pero siempre primero las de mayor weight
curl "http://localhost:8080/api/catalogs/category?sortDirection=DESC"

# Marcas ordenadas por web
curl "http://localhost:8080/api/catalogs/brand?sortBy=website"

# Marcas cuya web contiene "logi" o "samsung"
curl "http://localhost:8080/api/catalogs/brand?filter=website:logi,samsung"

# Gestión: marcas desactivadas con web .es
curl -u admin:admin "http://localhost:8080/admin/api/catalogs/brand?filter=enabled:false;website:.es"

# Segunda página de 10 marcas, por peso descendente (-i para ver las cabeceras X-*)
curl -i "http://localhost:8080/api/catalogs/brand?paged=true&page=1&size=10&sortBy=weight&sortDirection=DESC"
```

### 4. Multi-idioma (i18n)

- Idiomas soportados: **`en`** (por defecto) y **`es`**.
- El cliente elige idioma con la cabecera **`Accept-Language`**. Afecta a:
  - el `name`/`description` devuelto en lecturas y listados;
  - el idioma sobre el que se filtra y ordena;
  - el texto de los **mensajes de error**.
- Si una entrada no tiene traducción en el idioma pedido, se usa la de `en`.
- Cada entrada **debe** tener traducción en `en`; las demás son opcionales.
- La API de gestión (`/admin/api/...`) devuelve todas las traducciones a la vez; `Accept-Language` solo afecta allí al filtro/orden por `name`/`description` y a los mensajes de error.

### 5. Campos adicionales por tipo

Cada tipo de catálogo puede tener campos propios además de los comunes:

| Tipo | Qué es | Campos adicionales |
|---|---|---|
| `brand` | Marca | `website` (texto) |
| `category` | Categoría | `colorCode` (texto) |
| `region` | Comunidad autónoma de España (incl. Ceuta y Melilla) | `ineCode` (entero, código INE 1–19) |
| `province` | Provincia de España (incl. Ceuta y Melilla) | `ineCode` (entero, código INE 1–52), `regionCode` (entero, `ineCode` de su comunidad) |

Por ejemplo, las provincias de Andalucía: `GET /api/catalogs/province?filter=regionCode:1`.

- Viajan en el JSON como atributos normales de primer nivel (`"website": "..."`), tanto en la petición como en la respuesta.
- Se pueden usar en `sortBy` y como filtro del listado (solo en su tipo; ver [punto 3](#3-consultas-filtrado-ordenación-y-paginación)).
- En POST/PUT se sobrescriben por completo: omitir uno lo deja vacío.
- Las claves que no correspondan a un campo del tipo se ignoran.

### 6. Actualización parcial con JSON Patch (RFC 6902)

`PATCH /admin/api/catalogs/{type}/{id}` con `Content-Type: application/json-patch+json`. Soporta las **seis operaciones** del estándar: `add`, `remove`, `replace`, `move`, `copy` y `test`.

El documento sobre el que se aplica el patch es el mismo payload de POST/PUT, así que las traducciones se direccionan **por idioma**:

| Objetivo | Operación |
|----------|-----------|
| Añadir/reemplazar la traducción española | `{"op":"add","path":"/translations/es","value":{"name":"...","description":"..."}}` |
| Cambiar solo la descripción en inglés | `{"op":"replace","path":"/translations/en/description","value":"..."}` |
| Quitar un idioma | `{"op":"remove","path":"/translations/es"}` |
| Cambiar el peso | `{"op":"replace","path":"/weight","value":20}` |
| Cambiar un campo extra | `{"op":"replace","path":"/website","value":"https://..."}` |
| Condición previa (bloqueo optimista) | `{"op":"test","path":"/translations/en/name","value":"Logitech"}` |

```bash
curl -X PATCH http://localhost:8080/admin/api/catalogs/brand/1 \
  -H "Content-Type: application/json-patch+json" \
  -d '[
    { "op": "test",    "path": "/translations/en/name", "value": "Logitech" },
    { "op": "replace", "path": "/weight", "value": 20 },
    { "op": "add",     "path": "/translations/es", "value": { "name": "Logitech", "description": "Periféricos" } }
  ]'
```

- El patch es **atómico**: si una operación falla, no se aplica ninguna.
- `id`, `type`, `createdAt` y `updatedAt` no forman parte del documento y no se pueden modificar: `replace`/`remove` sobre ellos falla con 400, y un `add` se ignora.
- El resultado se valida con las mismas reglas que POST/PUT (p. ej. quitar `/translations/en` → 400).

| Status | Cuándo |
|--------|--------|
| 200 | Patch aplicado |
| 400 | Documento inválido, operación no aplicable o resultado inválido |
| 404 | Entrada no encontrada |
| 409 | Una operación `test` falló, o el nombre resultante ya existe |
| 415 | `Content-Type` distinto de `application/json-patch+json` |

### 7. Validaciones y reglas de negocio

| Regla | Respuesta |
|-------|-----------|
| `translations` presente y no vacío | 400 |
| `translations` incluye `en` y solo idiomas soportados (`en`, `es`) | 400 |
| `name` obligatorio, no vacío, máx. 80 caracteres | 400 |
| `description` máx. 500 caracteres | 400 |
| **Nombre único** por tipo e idioma, sin distinguir mayúsculas (dos marcas no pueden llamarse "Logitech" en `en`; una marca y una categoría sí) | 409 |
| `sortBy` debe ser un campo ordenable del tipo | 422 |
| Los filtros de campo adicional deben nombrar un campo del tipo, con un operador y un valor válidos para su tipo de dato | 422 |
| `sortDirection` debe ser `ASC` o `DESC` | 400 |
| No se puede eliminar una entrada referenciada por otra tabla | 409 |

### 8. Errores

Todas las respuestas de error tienen el mismo formato, con `message` localizado según `Accept-Language`:

```json
{
  "timestamp": "2026-10-01T12:34:56Z",
  "status": 409,
  "error": "Conflict",
  "message": "Marca: ya existe un registro con el nombre 'Logitech'",
  "errors": ["..."]
}
```

`errors` solo aparece con el detalle de los campos inválidos (validaciones).

| Status | Significado |
|--------|-------------|
| 400 | Petición mal formada: JSON inválido, validación fallida, tipo de catálogo desconocido, parámetro inválido, JSON Patch inválido |
| 401 | Sin credenciales o credenciales incorrectas en una ruta protegida (con cabecera `WWW-Authenticate: Basic`) — ver [punto 11](#11-seguridad) |
| 403 | Autenticado, pero sin el rol requerido (`ADMIN`) |
| 404 | Entrada no encontrada |
| 409 | Nombre duplicado, `test` de JSON Patch fallido, o entrada referenciada al eliminar |
| 415 | `Content-Type` no soportado |
| 422 | Consulta bien formada pero no válida (`sortBy` no ordenable, filtro de campo adicional no válido) |
| 500 | Error interno |

### 9. Caché

Las lecturas más frecuentes se sirven desde una caché en memoria: `GET /admin/api/catalogs`, `GET .../{type}/{id}` y los listados **sin `filter`** (los de los selects; la API de consulta añade internamente `enabled:true`, que no cuenta). **Cada tipo de catálogo tiene sus propias cachés y su propia configuración.** Un alta, modificación o borrado por la API de gestión invalida solo las cachés de ese tipo, al confirmarse la transacción.

La configuración no depende de la librería de caché (hoy Caffeine; cambiarla es añadir un adaptador — ver [ARCHITECTURE.md](docs/ARCHITECTURE.md#aislamiento-de-la-librería-de-caché)):

```yaml
app:
  catalog:
    cache:                   # por defecto para todos los tipos
      provider: caffeine
      enabled: true
      maximum-size: 1000
      expire-after-write: 10m
      stats: true
    types:
      category:
        cache:               # solo lo que cambia; el resto se hereda campo a campo
          maximum-size: 500
          expire-after-write: 1h
          # enabled: false   → category no se cachea
```

- Campos: `enabled`, `maximum-size`, `expire-after-write`, `expire-after-access`, `stats` y `native-options` (vía de escape con opciones propias de la librería).
- Con varias instancias, o si se modifica la base de datos directamente, un cambio puede tardar hasta la caducidad del tipo en verse en las demás instancias.
- **Estado en Actuator**: `GET /actuator/catalogcaches` (o `/actuator/catalogcaches/{type}`) muestra el proveedor, la configuración efectiva de cada tipo y, por caché, tamaño y aciertos/fallos (`stats`, si `stats: true`). También `/actuator/caches` (lista estándar de Spring, en solo lectura) y `/actuator/metrics/cache.gets`.

```json
"brand": {
  "settings": { "enabled": true, "maximumSize": 1000, "expireAfterWrite": "PT10M", "expireAfterAccess": null,
                "stats": true, "nativeOptions": {} },
  "caches": {
    "catalogById:brand":  { "active": true, "size": 12,
                            "stats": { "requests": 40, "hits": 28, "misses": 12, "hitRate": 0.7, "evictions": 0 } },
    "catalogLists:brand": { "...": "..." }
  }
}
```

Detalles en [ARCHITECTURE.md](docs/ARCHITECTURE.md#caché-de-lecturas).

### 10. Añadir un nuevo tipo de catálogo

Sin escribir controller, servicio ni repositorio:

1. Crear una clase `@Entity` en `io.masterdata.catalog`, copiando `BrandEntity`: sus traducciones se heredan de `CatalogBaseEntity` y se guardan en `<tipo>_translations` con FK `<tipo>_id` (nombre de clase sin `Entity`, ver `CatalogImplicitNamingStrategy`), sin declarar nada. Las columnas propias del tipo (p. ej. `hexCode`) se declaran en la entidad y se exponen solas en la API, sin configurarlas.
2. Añadir el tipo en `application.yml`:
   ```yaml
   app:
     catalog:
       types:
         color:
           entity-class: io.masterdata.catalog.ColorEntity
           names:                    # opcional: nombre del tipo en los mensajes de error
             en: Color
             es: Color
   ```
3. **(Opcional) Caché propia** en la misma entrada: `cache: {maximum-size: ..., expire-after-write: ..., enabled: ...}` (ver [punto 9](#9-caché)); sin ella hereda `app.catalog.cache`.
4. Nada más: los errores 404/409 usan las claves genéricas `error.catalog.notFound`/`error.catalog.duplicateName`, así que no hace falta definir mensajes por tipo.

`/api/catalogs/color` y `/admin/api/catalogs/color` quedan disponibles con todas las operaciones anteriores.

---

### 11. Seguridad

Spring Security con **HTTP Basic**, sin estado (sin sesión ni cookies, y por tanto sin CSRF):

| Ruta | Acceso |
|------|--------|
| `/api/**` (consulta) | pública |
| `/admin/**` (gestión) | rol `ADMIN` |
| `/actuator/health`, `/actuator/info` | públicos (los detalles de health, solo `ADMIN`) |
| resto de `/actuator/**` | rol `ADMIN` |
| Swagger UI y `/v3/api-docs/**` | públicos |
| cualquier otra ruta | autenticada |

Los usuarios se declaran en `application.yml` (`app.security.users`), con la contraseña prefijada por el algoritmo (`{bcrypt}…` o, solo para pruebas, `{noop}…`):

```yaml
app:
  security:
    users:
      admin:
        password: "{bcrypt}$2a$10$..."
        roles: [ADMIN]
```

En desarrollo hay un usuario **`admin` / `admin`**. Fuera de desarrollo hay que cambiarlo, p. ej. con la variable de entorno `APP_SECURITY_USERS_ADMIN_PASSWORD='{bcrypt}<hash>'`.

```bash
curl -u admin:admin http://localhost:8080/admin/api/catalogs/brand
```

Los 401/403 usan el mismo formato de error y los mismos mensajes localizados que el resto de la API ([punto 8](#8-errores)). En Swagger UI, la API de gestión muestra el botón **Authorize** para introducir las credenciales.

## Stack
- Java 17
- Spring Boot 4.1.0 (Web, Validation, Data JPA + Hibernate, Actuator, Security)
- PostgreSQL (ejecución) / H2 en memoria (solo tests)
- Lombok
- springdoc-openapi (Swagger UI)
- Caffeine (caché en memoria, vía Spring Cache; la librería es intercambiable)

## Requirements
- JDK 17+
- Maven 3.9+
- PostgreSQL

## Base de datos
Por defecto la app se conecta a `jdbc:postgresql://localhost:5432/postgres` (usuario `postgres`, contraseña `secret`; ver [application.yml](src/main/resources/application.yml)). Hibernate crea/actualiza las tablas automáticamente (`ddl-auto: update`).

El repositorio incluye un **devcontainer** ([.devcontainer](.devcontainer)) con Java 17 + Maven y PostgreSQL 16 (puertos 8080 y 5432). Ese contenedor crea la base `ej1` con contraseña `postgres`, que no coincide con los valores por defecto de `application.yml`: ajusta `spring.datasource.*` (o usa variables de entorno como `SPRING_DATASOURCE_URL`).

## Run
```bash
mvn spring-boot:run
```
La app arranca en http://localhost:8080.

Para cargar marcas y categorías de ejemplo, y las 19 comunidades/ciudades autónomas y 52 provincias de España (con la app arrancada):

```bash
./scripts/seed-catalogs.sh
```

En Windows: `.\scripts\seed-catalogs.ps1`. Los datos de comunidades y provincias (con sus códigos INE) están en `scripts/data/*.csv`, compartidos por ambos scripts.

Los scripts usan la API de gestión con el usuario de desarrollo `admin`/`admin`; para otro usuario, define `SEED_USER` y `SEED_PASSWORD` (o, en PowerShell, `-User`/`-Password`).

## API Docs (Swagger)
- Swagger UI: http://localhost:8080/swagger-ui.html — un desplegable arriba a la derecha permite cambiar entre las dos APIs.
- OpenAPI spec (JSON), un documento por API:
  - Consulta: http://localhost:8080/v3/api-docs/consulta
  - Gestión: http://localhost:8080/v3/api-docs/gestion

Cada operación documenta la cabecera `Accept-Language` con los idiomas soportados.

Monitorización (Actuator): `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/caches` y `/actuator/catalogcaches` (estado de las cachés por tipo, ver [punto 9](#9-caché)). Salvo `health` e `info`, requieren rol `ADMIN` ([punto 11](#11-seguridad)); por eso `/actuator/caches` admite también `DELETE` para vaciar cachés (`management.endpoint.caches.access: unrestricted`).

## Build y tests
```bash
mvn clean package
```

```bash
mvn test
```

> **Aviso:** en `src/test/java/io/masterdata/product/**` quedan tests de un módulo `product` cuyo código ya no existe en `src/main`. Mientras no se eliminen (o se restaure el módulo), la compilación de tests falla.

## Arquitectura
Arquitectura hexagonal (Ports & Adapters). Ver [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para las capas, reglas de dependencia, el diseño del catálogo genérico, los mappers, el manejo de errores e i18n, y la implementación de JSON Patch.
