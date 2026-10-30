# Catalog admin (Angular 21)

Backoffice UI over `/admin/api/catalogs`: pick a catalog type, then list / create / edit / disable its entries.
Columns and form fields are built from the type definitions returned by `GET /admin/api/catalogs`.

```bash
npm install        # .npmrc sets legacy-peer-deps (npm 10 trips over vitest's jsdom peer otherwise)
npm start          # http://localhost:4200, proxies /admin to http://localhost:8080
npm test
```

The **Monitoring** section reads `/actuator/**` (proxied too): health, uptime, heap/CPU/threads, DB pool, requests per catalog
(needs the backend's `catalog` metric tag, so restart the backend after updating it) and the caches, which can be cleared one by one or all at once.
It refreshes every 10 s. With `app.catalog.cache.enabled=false` there are no caches to list.

Sign in with a backend `ADMIN` user (HTTP Basic; credentials live in `sessionStorage`).
Delete is the backend's logical delete (`enabled=false`); edit the entry to restore it.
PUT/DELETE send `If-Match` with the entry's version, so a concurrent change shows a 412/409 message and reloads the list.
