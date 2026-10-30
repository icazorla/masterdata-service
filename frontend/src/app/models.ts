/** Locales editable in the form. `en` is the default language and must always be present. */
// shortcut: hardcoded to app.i18n.supported-locales (en,es), derive it from the API if more languages are added.
export const LOCALES = ['en', 'es'];

export interface CatalogField {
  name: string;
  dataType: 'string' | 'integer' | 'number' | 'boolean' | 'date' | 'date-time';
  required: boolean;
  readOnly: boolean;
  translatable: boolean;
  sortable: boolean;
  extra: boolean;
  filterOperators: string[];
}

export interface CatalogType {
  type: string;
  name: string;
  fields: CatalogField[];
}

export interface Translation {
  name: string;
  description?: string | null;
}

/** An entry as returned by the admin API; the type's extra fields come flattened at top level. */
export interface CatalogEntry {
  id: number;
  weight: number;
  enabled: boolean;
  translations: Record<string, Translation>;
  createdAt: string;
  updatedAt: string;
  version: number;
  [extra: string]: unknown;
}

export interface Draft {
  weight: number;
  enabled: boolean;
  translations: Record<string, Translation>;
  extras: Record<string, unknown>;
}

export function newDraft(type: CatalogType, entry?: CatalogEntry): Draft {
  const translations: Record<string, Translation> = {};
  for (const l of LOCALES) {
    translations[l] = { name: entry?.translations[l]?.name ?? '', description: entry?.translations[l]?.description ?? '' };
  }
  const extras: Record<string, unknown> = {};
  for (const f of extraFields(type)) {
    extras[f.name] = entry?.[f.name] ?? (f.dataType === 'boolean' ? false : null);
  }
  return { weight: entry?.weight ?? 0, enabled: entry?.enabled ?? true, translations, extras };
}

export function extraFields(type: CatalogType): CatalogField[] {
  return type.fields.filter((f) => f.extra && !f.readOnly);
}

/** Request body: locales with an empty name are dropped, empty extras are omitted (a PUT overwrites them all). */
export function toPayload(draft: Draft): Record<string, unknown> {
  const translations: Record<string, Translation> = {};
  for (const [locale, t] of Object.entries(draft.translations)) {
    if (t.name?.trim()) {
      translations[locale] = { name: t.name.trim(), description: t.description?.trim() || null };
    }
  }
  const extras = Object.fromEntries(Object.entries(draft.extras).filter(([, v]) => v !== null && v !== ''));
  return { weight: draft.weight ?? 0, enabled: draft.enabled, translations, ...extras };
}
