import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Auth } from './auth';
import { CatalogApi, errorMessage } from './catalog-api';
import { CatalogCrud } from './catalog-crud';
import { I18n, LANGS } from './i18n';
import { CatalogType } from './models';

@Component({
  selector: 'app-root',
  imports: [FormsModule, CatalogCrud],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(Auth);
  protected readonly i18n = inject(I18n);
  private readonly api = inject(CatalogApi);

  protected readonly langs = Object.entries(LANGS).map(([code, name]) => ({ code, name }));
  protected user = 'admin';
  protected password = '';
  protected readonly types = signal<CatalogType[]>([]);
  protected readonly selectedCode = signal<string | null>(null);
  protected readonly selected = computed(() => this.types().find((t) => t.type === this.selectedCode()));
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Type names come translated by the backend (Accept-Language): load them at startup and on every language change.
    effect(() => {
      this.i18n.lang();
      untracked(() => this.auth.header() && this.loadTypes());
    });
  }

  protected login() {
    this.auth.login(this.user, this.password);
    this.password = '';
    this.loadTypes();
  }

  protected logout() {
    this.auth.logout();
    this.types.set([]);
    this.selectedCode.set(null);
  }

  private loadTypes() {
    this.error.set(null);
    this.api.types().subscribe({
      next: (types) => this.types.set(types),
      // The interceptor already logged out on 401.
      error: (e) =>
        this.error.set(e.status === 401 ? this.i18n.t('invalidCredentials') : errorMessage(e, this.i18n.t('backendUnavailable'))),
    });
  }
}
