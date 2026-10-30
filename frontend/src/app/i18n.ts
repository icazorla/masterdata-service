import { Injectable, effect, signal } from '@angular/core';

export type Lang = 'en' | 'es';
export const LANGS: Record<Lang, string> = { en: 'English', es: 'Español' };

const en = {
  appTitle: 'Catalogs',
  language: 'Language',
  catalog: 'Catalog',
  selectCatalog: 'Select a catalog…',
  pickCatalogHint: 'Select a catalog to manage its entries.',
  signIn: 'Sign in',
  signOut: 'Sign out',
  user: 'User',
  password: 'Password',
  invalidCredentials: 'Invalid credentials.',
  backendUnavailable: 'Backend unavailable.',
  name: 'Name',
  description: 'Description',
  weight: 'Weight',
  enabled: 'Enabled',
  yes: 'Yes',
  no: 'No',
  any: 'any',
  anyOf: '(a,b = any)',
  search: 'Search',
  clear: 'Clear',
  new: 'New',
  edit: 'Edit',
  disable: 'Disable',
  noEntries: 'No entries.',
  prev: '‹ Prev',
  next: 'Next ›',
  page: 'Page {0} / {1} · {2} entries',
  pageSize: 'Page size',
  perPage: '{0} per page',
  required: 'required',
  cancel: 'Cancel',
  save: 'Save',
  saving: 'Saving…',
  loadError: 'Could not load the list.',
  confirmDisable: 'Disable "{0}"? It is a logical delete (enabled=false); edit it to restore it.',
  isoPlaceholder: 'ISO-8601, e.g. 2024-01-31T10:00:00Z',
};

type Key = keyof typeof en;

const es: Record<Key, string> = {
  appTitle: 'Catálogos',
  language: 'Idioma',
  catalog: 'Catálogo',
  selectCatalog: 'Selecciona un catálogo…',
  pickCatalogHint: 'Selecciona un catálogo para gestionar sus entradas.',
  signIn: 'Iniciar sesión',
  signOut: 'Cerrar sesión',
  user: 'Usuario',
  password: 'Contraseña',
  invalidCredentials: 'Credenciales no válidas.',
  backendUnavailable: 'Backend no disponible.',
  name: 'Nombre',
  description: 'Descripción',
  weight: 'Peso',
  enabled: 'Activo',
  yes: 'Sí',
  no: 'No',
  any: 'cualquiera',
  anyOf: '(a,b = cualquiera)',
  search: 'Buscar',
  clear: 'Limpiar',
  new: 'Nuevo',
  edit: 'Editar',
  disable: 'Deshabilitar',
  noEntries: 'No hay entradas.',
  prev: '‹ Anterior',
  next: 'Siguiente ›',
  page: 'Página {0} / {1} · {2} entradas',
  pageSize: 'Tamaño de página',
  perPage: '{0} por página',
  required: 'obligatorio',
  cancel: 'Cancelar',
  save: 'Guardar',
  saving: 'Guardando…',
  loadError: 'No se pudo cargar el listado.',
  confirmDisable: '¿Deshabilitar "{0}"? Es un borrado lógico (enabled=false); edítala para restaurarla.',
  isoPlaceholder: 'ISO-8601, p. ej. 2024-01-31T10:00:00Z',
};

const DICT: Record<Lang, Record<Key, string>> = { en, es };
const STORAGE_KEY = 'catalog-admin.lang';

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'es') return saved;
  } catch {
    // storage unavailable: fall through to the browser language
  }
  return navigator.language.startsWith('es') ? 'es' : 'en';
}

/**
 * UI language (runtime switch, so not Angular's build-time i18n). It is also sent to the backend as
 * Accept-Language: type names, error messages and the name sort/filter follow it.
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>(initialLang());

  constructor() {
    effect(() => {
      const lang = this.lang();
      document.documentElement.lang = lang;
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // not persisted, the choice still applies to this session
      }
    });
  }

  t(key: Key, ...args: (string | number)[]): string {
    return args.reduce<string>((text, arg, i) => text.replace(`{${i}}`, String(arg)), DICT[this.lang()][key]);
  }
}
