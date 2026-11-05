import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { CatalogApi, ListQuery } from './catalog-api';

function filterSentFor(filters: Record<string, string>) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const query: ListQuery = { page: 0, size: 20, sortBy: 'name', sortDirection: 'ASC', filters };
  TestBed.inject(CatalogApi).list('province', query).subscribe();
  const req = TestBed.inject(HttpTestingController).expectOne((r) => r.url === '/admin/api/catalogs/province');
  return req.request.params.get('filter');
}

describe('CatalogApi.list filter', () => {
  it('joins name and extra fields with ";", skipping empty ones', () => {
    expect(filterSentFor({ name: 'sev', regionCode: '1,8', other: ' ' })).toBe('name:sev;regionCode:1,8');
  });

  it('escapes ";" and "\\" but not ","', () => {
    expect(filterSentFor({ name: 'a;b\\c,d' })).toBe('name:a\\;b\\\\c,d');
  });

  it('sends no filter when nothing is typed', () => {
    expect(filterSentFor({})).toBeNull();
  });
});
