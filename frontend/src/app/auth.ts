import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { tap } from 'rxjs';

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

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const header = auth.header();
  return next(header ? req.clone({ setHeaders: { Authorization: header } }) : req).pipe(
    tap({ error: (e) => e.status === 401 && auth.logout() }),
  );
};
