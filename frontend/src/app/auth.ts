import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { tap } from 'rxjs';

import { I18n } from './i18n';

const KEY = 'catalog-admin.auth';

/** HTTP Basic credentials of the backend's ADMIN user, kept in sessionStorage (cleared when the tab closes). */
@Injectable({ providedIn: 'root' })
export class Auth {
  readonly header = signal<string | null>(sessionStorage.getItem(KEY));

  login(user: string, password: string) {
    const value = 'Basic ' + btoa(`${user}:${password}`);
    sessionStorage.setItem(KEY, value);
    this.header.set(value);
  }

  logout() {
    sessionStorage.removeItem(KEY);
    this.header.set(null);
  }
}

/** Adds the credentials and the UI language to every request; a 401 drops the credentials. */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const header = auth.header();
  const headers: Record<string, string> = { 'Accept-Language': inject(I18n).lang() };
  if (header) headers['Authorization'] = header;
  return next(req.clone({ setHeaders: headers })).pipe(tap({ error: (e) => e.status === 401 && auth.logout() }));
};
