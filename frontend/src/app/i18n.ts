import { Injectable, effect, signal } from '@angular/core';

export type Lang = 'en' | 'es';
export const LANGS: Record<Lang, string> = { en: 'English', es: 'Español' };

const en = {
  appTitle: 'Catalogs',
  language: 'Language',
  catalog: 'Catalog',
  selectCatalog: 'Select a catalog…',
  homeTitle: 'Which catalog do you want to manage?',
  homeHint: 'Pick one to view, search and edit its entries.',
  manage: 'Manage',
  backToCatalogs: '← Catalogs',
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
  page: 'Page',
  entries: '{0} entries',
  pageSize: 'Page size',
  perPage: '{0} per page',
  createdAt: 'Created',
  updatedAt: 'Last modified',
  version: 'Version',
  navCatalogs: 'Catalogs',
  navMonitoring: 'Monitoring',
  refresh: 'Refresh',
  autoRefresh: 'Auto-refresh (10 s)',
  snapshotAt: 'Updated {0}',
  loading: 'Loading…',
  health: 'Health',
  uptime: 'Uptime',
  heap: 'Heap memory',
  cpu: 'Process CPU',
  threads: 'Threads',
  traffic: 'Traffic and latency',
  requestsPerSecond: 'Requests / s',
  reqPerSecShort: 'Req/s',
  latencyP95: 'p95 latency',
  conflicts: 'Conflicts 409 / 412',
  serverErrors: '5xx errors',
  trafficHint: 'Trend lines: readings since this screen was opened. p95/p99: recent window (about 2 min); the rest counts since startup.',
  byMethod: 'By method',
  byStatus: 'By status code',
  conflictsHint: '409 and 412 are version conflicts between editors.',
  requestsByCatalog: 'Requests per catalog',
  requests: 'Requests',
  avgTime: 'Avg time',
  maxTime: 'Max time',
  requestsHint: 'Since the service started, API requests only (not monitoring calls).',
  share: 'Share',
  distribution: 'Distribution',
  jvm: 'JVM',
  healthChecks: 'Health checks',
  noData: 'No data yet.',
  pool: 'Database pool',
  poolActive: 'Connections in use / max',
  poolIdle: 'Idle connections',
  poolPending: 'Threads waiting',
  poolTimeouts: 'Wait timeouts',
  caches: 'Caches',
  cache: 'Cache',
  cacheEntries: 'Entries',
  hits: 'Hits',
  misses: 'Misses',
  hitRatio: 'Hit ratio',
  clearCache: 'Clear',
  clearAll: 'Clear all',
  actuatorUnreachable: 'Could not read /actuator. Check that the backend is running and that you are signed in as ADMIN. In development, restart "npm start": the proxy is only read at startup.',
  noCaches: 'No caches are active (app.catalog.cache.enabled=false).',
  confirmClearAll: 'Clear all caches?',
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
  homeTitle: '¿Qué catálogo quieres gestionar?',
  homeHint: 'Elige uno para ver, buscar y editar sus entradas.',
  manage: 'Gestionar',
  backToCatalogs: '← Catálogos',
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
  page: 'Página',
  entries: '{0} entradas',
  pageSize: 'Tamaño de página',
  perPage: '{0} por página',
  createdAt: 'Creado',
  updatedAt: 'Última modificación',
  version: 'Versión',
  navCatalogs: 'Catálogos',
  navMonitoring: 'Monitorización',
  refresh: 'Actualizar',
  autoRefresh: 'Actualizar cada 10 s',
  snapshotAt: 'Actualizado a las {0}',
  loading: 'Cargando…',
  health: 'Estado',
  uptime: 'Tiempo en marcha',
  heap: 'Memoria heap',
  cpu: 'CPU del proceso',
  threads: 'Hilos',
  traffic: 'Tráfico y latencia',
  requestsPerSecond: 'Peticiones / s',
  reqPerSecShort: 'Pet./s',
  latencyP95: 'Latencia p95',
  conflicts: 'Conflictos 409 / 412',
  serverErrors: 'Errores 5xx',
  trafficHint: 'Gráficas: lecturas desde que abriste esta pantalla. p95/p99: ventana reciente (unos 2 min); el resto cuenta desde el arranque.',
  byMethod: 'Por método',
  byStatus: 'Por código de estado',
  conflictsHint: '409 y 412 son conflictos de versión entre editores.',
  requestsByCatalog: 'Peticiones por catálogo',
  requests: 'Peticiones',
  avgTime: 'Tiempo medio',
  maxTime: 'Tiempo máximo',
  requestsHint: 'Desde el arranque del servicio, solo peticiones de la API (no las de monitorización).',
  share: 'Reparto',
  distribution: 'Distribución',
  jvm: 'JVM',
  healthChecks: 'Salud',
  noData: 'Sin datos todavía.',
  pool: 'Pool de base de datos',
  poolActive: 'Conexiones en uso / máx.',
  poolIdle: 'Conexiones libres',
  poolPending: 'Hilos esperando',
  poolTimeouts: 'Esperas agotadas',
  caches: 'Cachés',
  cache: 'Caché',
  cacheEntries: 'Entradas',
  hits: 'Aciertos',
  misses: 'Fallos',
  hitRatio: 'Ratio de aciertos',
  clearCache: 'Vaciar',
  clearAll: 'Vaciar todas',
  actuatorUnreachable: 'No se pudo leer /actuator. Comprueba que el backend está arrancado y que has entrado como ADMIN. En desarrollo, reinicia "npm start": el proxy solo se lee al arrancar.',
  noCaches: 'No hay cachés activas (app.catalog.cache.enabled=false).',
  confirmClearAll: '¿Vaciar todas las cachés?',
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
