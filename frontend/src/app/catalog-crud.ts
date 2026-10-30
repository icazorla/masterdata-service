import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { CatalogApi, ListQuery, errorMessage } from './catalog-api';
import { I18n } from './i18n';
import { CatalogEntry, CatalogType, Draft, LOCALES, extraFields, newDraft, toPayload } from './models';

const inputTypes = { string: 'text', integer: 'number', number: 'number', boolean: 'checkbox', date: 'date', 'date-time': 'text' };

/** List + create/edit form + delete for one catalog type. Columns and form fields come from the type's definition. */
@Component({
  selector: 'app-catalog-crud',
  imports: [FormsModule],
  templateUrl: './catalog-crud.html',
  styleUrl: './catalog-crud.css',
})
export class CatalogCrud {
  private readonly api = inject(CatalogApi);
  protected readonly i18n = inject(I18n);

  readonly type = input.required<CatalogType>();
  protected readonly locales = LOCALES;
  protected readonly extras = computed(() => extraFields(this.type()));

  /** Back to page 0 / default sort whenever another catalog is selected (not when only its translated name changes). */
  protected readonly query = linkedSignal<string, ListQuery>({
    source: () => this.type().type,
    computation: () => ({ page: 0, size: 20, sortBy: 'name', sortDirection: 'ASC', filters: {} }),
  });
  /** Search inputs, applied to `query` on submit; reset with the query when another catalog is selected. */
  protected readonly filters = linkedSignal<string, Record<string, string>>({ source: () => this.type().type, computation: () => ({}) });
  protected readonly filterable = computed(() => this.extras().filter((f) => f.filterOperators.length > 0));

  protected readonly page = rxResource({
    // lang: the list's name sort/filter follow Accept-Language, so refetch when it changes.
    params: () => ({ type: this.type().type, query: this.query(), lang: this.i18n.lang() }),
    stream: ({ params }) => this.api.list(params.type, params.query),
  });
  protected readonly pages = computed(() => Math.max(1, Math.ceil((this.page.value()?.total ?? 0) / this.query().size)));

  protected readonly editing = signal<{ entry?: CatalogEntry; draft: Draft } | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected inputType(dataType: keyof typeof inputTypes) {
    return inputTypes[dataType];
  }

  protected arrow(field: string) {
    const q = this.query();
    return q.sortBy === field ? (q.sortDirection === 'ASC' ? ' ▲' : ' ▼') : '';
  }

  protected applySearch() {
    this.query.update((q) => ({ ...q, filters: this.filters(), page: 0 }));
  }

  protected clearSearch() {
    this.filters.set({});
    this.applySearch();
  }

  protected setFilter(field: string, value: string) {
    this.filters.update((f) => ({ ...f, [field]: value }));
  }

  protected sortBy(field: string) {
    this.query.update((q) => ({
      ...q,
      sortBy: field,
      sortDirection: q.sortBy === field && q.sortDirection === 'ASC' ? 'DESC' : 'ASC',
      page: 0,
    }));
  }

  protected goTo(page: number) {
    this.query.update((q) => ({ ...q, page }));
  }

  protected create() {
    this.error.set(null);
    this.editing.set({ draft: newDraft(this.type()) });
  }

  protected edit(entry: CatalogEntry) {
    this.error.set(null);
    this.editing.set({ entry, draft: newDraft(this.type(), entry) });
  }

  protected save() {
    const { entry, draft } = this.editing()!;
    const body = toPayload(draft);
    this.saving.set(true);
    this.error.set(null);
    const request = entry ? this.api.update(this.type().type, entry, body) : this.api.create(this.type().type, body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.page.reload();
      },
      error: (e) => this.fail(e),
    });
  }

  protected remove(entry: CatalogEntry) {
    const name = entry.translations['en']?.name ?? entry.id;
    if (!confirm(this.i18n.t('confirmDisable', name))) return;
    this.error.set(null);
    this.api.delete(this.type().type, entry).subscribe({
      next: () => this.page.reload(),
      error: (e) => this.fail(e),
    });
  }

  /** 409/412 mean the entry changed under us: show the message and reload so the list has the new version. */
  private fail(e: unknown) {
    this.saving.set(false);
    this.error.set(errorMessage(e, this.i18n.t('backendUnavailable')));
    this.page.reload();
  }
}
