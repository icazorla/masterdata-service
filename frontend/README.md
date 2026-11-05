# Catalog admin (Angular 21)

Backoffice UI over `/admin/api/catalogs`: pick a catalog type, then list / create / edit / disable its entries.
Columns and form fields are built from the type definitions returned by `GET /admin/api/catalogs`.

```bash
npm install        # .npmrc sets legacy-peer-deps (npm 10 trips over vitest's jsdom peer otherwise)
npm start          # http://localhost:4200, proxies /admin to http://localhost:8080
npm test
```

To serve the UI from the Spring Boot service itself (one process, one port), run `mvn -Pui spring-boot:run` from the repo root:
the `ui` profile builds this app and copies it to `static/`. `npm start` stays the way to work on it with live reload.

The **Monitoring** section reads `/actuator/**` (proxied too): health, uptime, heap/CPU/threads, DB pool, requests per catalog
(needs the backend's `catalog` metric tag, so restart the backend after updating it) and the caches, which can be cleared one by one or all at once.
It refreshes every 10 s. With `app.catalog.cache.enabled=false` there are no caches to list.
The screen is ordered by importance: header with health/version/uptime, traffic cards with trend lines (amber on conflicts or p95 > 500 ms,
red on 5xx), requests per catalog next to the method/status distribution, JVM / DB pool / health checks, and the caches last.
Thresholds (`P95_WARN_MS`, the 75 % / 90 % bar levels) live in `src/app/monitoring.ts`; see the root README, section 12.

Sign in with a backend `ADMIN` user (HTTP Basic; credentials live in `sessionStorage`).
Delete is the backend's logical delete (`enabled=false`); edit the entry to restore it.
PUT/DELETE send `If-Match` with the entry's version, so a concurrent change shows a 412/409 message and reloads the list.
