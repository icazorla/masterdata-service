import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { CatalogType } from './models';

const BASE = '/actuator';

export interface Health {
  status: string;
  components?: Record<string, { status: string }>;
}

export interface CacheRef {
  manager: string;
  name: string;
}

export interface CacheStats extends CacheRef {
  size: number | null;
  hits: number | null;
  misses: number | null;
}

export interface CatalogRequests {
  type: string;
  name: string;
  count: number;
  avgMs: number;
  maxMs: number;
  clientErrors: number;
  serverErrors: number;
}

export interface Snapshot {
  health: Health | null;
  build: { version?: string; time?: string } | null;
  uptimeSeconds: number | null;
  heapUsed: number | null;
  heapMax: number | null;
  threads: number | null;
  cpu: number | null;
  pool: Record<'active' | 'idle' | 'max' | 'pending' | 'timeout', number | null>;
  /** All zeros until the first request, or when the backend doesn't tag requests by `catalog` (older build). */
  requests: CatalogRequests[];
  caches: CacheStats[];
  takenAt: Date;
}

type Stats = Record<string, number>;

/** Reads what Spring Boot Actuator exposes (`/actuator/**`, ADMIN role). Anything missing or failing comes back as null/empty. */
@Injectable({ providedIn: 'root' })
export class MonitoringApi {
  private readonly http = inject(HttpClient);

  /** statistic → value of a metric (`COUNT`, `TOTAL_TIME`, `MAX` for timers, `VALUE` for gauges); null if it doesn't exist. */
  private measure(name: string, ...tags: string[]): Observable<Stats | null> {
    const params = tags.reduce((p, tag) => p.append('tag', tag), new HttpParams());
    return this.http.get<{ measurements: { statistic: string; value: number }[] }>(`${BASE}/metrics/${name}`, { params }).pipe(
      map((r) => Object.fromEntries(r.measurements.map((m) => [m.statistic, m.value]))),
      catchError(() => of(null)),
    );
  }

  private value(name: string, ...tags: string[]) {
    return this.measure(name, ...tags).pipe(map((s) => s?.['VALUE'] ?? null));
  }

  private health(): Observable<Health | null> {
    return this.http.get<Health>(`${BASE}/health`).pipe(
      // DOWN comes as a 503 with the same body.
      catchError((e) => of(e instanceof HttpErrorResponse && e.error?.status ? (e.error as Health) : null)),
    );
  }

  private build() {
    return this.http.get<{ build?: { version?: string; time?: string } }>(`${BASE}/info`).pipe(
      map((i) => i.build ?? null),
      catchError(() => of(null)),
    );
  }

  private cacheRefs(): Observable<CacheRef[]> {
    type Body = { cacheManagers: Record<string, { caches: Record<string, unknown> }> };
    return this.http.get<Body>(`${BASE}/caches`).pipe(
      map((b) => Object.entries(b.cacheManagers).flatMap(([manager, m]) => Object.keys(m.caches).map((name) => ({ manager, name })))),
      catchError(() => of([])),
    );
  }

  private cacheStats(ref: CacheRef): Observable<CacheStats> {
    const cache = `cache:${ref.name}`;
    return forkJoin([
      this.measure('cache.size', cache),
      this.measure('cache.gets', cache, 'result:hit'),
      this.measure('cache.gets', cache, 'result:miss'),
    ]).pipe(map(([size, hits, misses]) => ({ ...ref, size: size?.['VALUE'] ?? null, hits: hits?.['COUNT'] ?? null, misses: misses?.['COUNT'] ?? null })));
  }

  private catalogRequests(type: CatalogType): Observable<CatalogRequests> {
    const tag = `catalog:${type.type}`;
    return forkJoin([
      this.measure('http.server.requests', tag),
      this.measure('http.server.requests', tag, 'outcome:CLIENT_ERROR'),
      this.measure('http.server.requests', tag, 'outcome:SERVER_ERROR'),
    ]).pipe(
      map(([all, client, server]) => {
        const count = all?.['COUNT'] ?? 0;
        return {
          type: type.type,
          name: type.name,
          count,
          avgMs: count ? ((all?.['TOTAL_TIME'] ?? 0) / count) * 1000 : 0,
          maxMs: (all?.['MAX'] ?? 0) * 1000,
          clientErrors: client?.['COUNT'] ?? 0,
          serverErrors: server?.['COUNT'] ?? 0,
        };
      }),
    );
  }

  snapshot(types: CatalogType[]): Observable<Snapshot> {
    const heap = ['area:heap'];
    return forkJoin({
      health: this.health(),
      build: this.build(),
      uptimeSeconds: this.value('process.uptime'),
      heapUsed: this.value('jvm.memory.used', ...heap),
      heapMax: this.value('jvm.memory.max', ...heap),
      threads: this.value('jvm.threads.live'),
      cpu: this.value('process.cpu.usage'),
      pool: forkJoin({
        active: this.value('hikaricp.connections.active'),
        idle: this.value('hikaricp.connections.idle'),
        max: this.value('hikaricp.connections.max'),
        pending: this.value('hikaricp.connections.pending'),
        timeout: this.measure('hikaricp.connections.timeout').pipe(map((s) => s?.['COUNT'] ?? null)),
      }),
      requests: types.length ? forkJoin(types.map((t) => this.catalogRequests(t))) : of([]),
      caches: this.cacheRefs().pipe(
        switchMap((refs) => (refs.length ? forkJoin(refs.map((r) => this.cacheStats(r))) : of([] as CacheStats[]))),
      ),
    }).pipe(map((s) => ({ ...s, takenAt: new Date() })));
  }

  /** Empties one cache, or all of them when `ref` is omitted. */
  clearCaches(ref?: CacheRef) {
    return ref
      ? this.http.delete<void>(`${BASE}/caches/${encodeURIComponent(ref.name)}`, { params: { cacheManager: ref.manager } })
      : this.http.delete<void>(`${BASE}/caches`);
  }
}
