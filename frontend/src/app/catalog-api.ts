import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';

import { CatalogEntry, CatalogType } from './models';

export interface Page {
  items: CatalogEntry[];
  total: number;
}

export interface ListQuery {
  page: number;
  size: number;
  sortBy: string;
  sortDirection: 'ASC' | 'DESC';
  /** field name → text typed by the user (`name` plus the type's filterable extra fields). */
  filters: Record<string, string>;
}

const BASE = '/admin/api/catalogs';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  types() {
    return this.http.get<CatalogType[]>(BASE);
  }

  list(type: string, q: ListQuery) {
    let params = new HttpParams()
      .set('page', q.page)
      .set('size', q.size)
      .set('sortBy', q.sortBy)
      .set('sortDirection', q.sortDirection);
    // filter = `field:value` conditions joined by ';' (AND). ';' and '\' inside a value are escaped; ',' is
    // left alone on purpose: "1,8" means "any of them".
    const filter = Object.entries(q.filters)
      .filter(([, v]) => v.trim())
      .map(([k, v]) => `${k}:${v.trim().replace(/[\\;]/g, '\\$&')}`)
      .join(';');
    if (filter) params = params.set('filter', filter);
    return this.http
      .get<CatalogEntry[]>(`${BASE}/${type}`, { params, observe: 'response' })
      .pipe(map((r): Page => ({ items: r.body ?? [], total: Number(r.headers.get('X-Total-Count') ?? 0) })));
  }

  create(type: string, body: unknown) {
    return this.http.post<CatalogEntry>(`${BASE}/${type}`, body);
  }

  /** `If-Match` carries the version we edited: 412 if someone else changed the entry meanwhile. */
  update(type: string, e: CatalogEntry, body: unknown) {
    return this.http.put<CatalogEntry>(`${BASE}/${type}/${e.id}`, body, { headers: { 'If-Match': `"${e.version}"` } });
  }

  /** Logical delete on the server (enabled=false). */
  delete(type: string, e: CatalogEntry) {
    return this.http.delete<void>(`${BASE}/${type}/${e.id}`, { headers: { 'If-Match': `"${e.version}"` } });
  }
}

/** Server error body is `{message, errors?}`; fall back to a generic text when the backend is unreachable. */
export function errorMessage(e: unknown): string {
  if (!(e instanceof HttpErrorResponse)) return String(e);
  if (e.status === 0 || e.status === 502 || e.status === 504) return 'Backend unavailable.';
  const body = e.error as { message?: string; errors?: string[] } | null;
  return [body?.message ?? e.message, ...(body?.errors ?? [])].join(' — ');
}
