import { KeyValuePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, filter, merge, switchMap, tap, timer } from 'rxjs';

import { I18n } from './i18n';
import { CatalogType } from './models';
import { CacheRef, CacheStats, MonitoringApi, Snapshot, Tally } from './monitoring-api';
import { Sparkline } from './sparkline';

const REFRESH_MS = 10_000;
/** Readings kept for the trend lines: 30 × 10 s = 5 min with auto-refresh on. */
const HISTORY = 30;
const METHOD_COLORS: Record<string, string> = { GET: '#1a56db', POST: '#eb6834', PUT: '#1baf7a', DELETE: '#eda100' };
const OTHER_METHOD_COLOR = '#98a2b3';
/** Statuses worth flagging: version conflicts between editors (409/412). */
const CONFLICTS = ['409', '412'];
/** Above this p95 the latency card turns amber. */
const P95_WARN_MS = 500;

/** What each reading adds to the trend lines; counters are cumulative, the lines show what changed between readings. */
interface Sample {
  at: number;
  total: number;
  byCatalog: Record<string, number>;
  p95Ms: number | null;
  conflicts: number;
  serverErrors: number;
}

function sampleOf(s: Snapshot): Sample {
  const sum = (tallies: Tally[]) => tallies.reduce((n, t) => n + t.count, 0);
  return {
    at: s.takenAt.getTime(),
    total: s.requests.reduce((n, r) => n + r.count, 0),
    byCatalog: Object.fromEntries(s.requests.map((r) => [r.type, r.count])),
    p95Ms: s.traffic.p95Ms,
    conflicts: sum(s.traffic.statuses.filter((t) => CONFLICTS.includes(t.name))),
    serverErrors: sum(s.traffic.statuses.filter((t) => t.name.startsWith('5'))),
  };
}

/** Service health and metrics from Spring Boot Actuator, plus cache reset. */
@Component({
  selector: 'app-monitoring',
  imports: [FormsModule, KeyValuePipe, Sparkline],
  templateUrl: './monitoring.html',
  styleUrl: './monitoring.css',
})
export class Monitoring {
  private readonly api = inject(MonitoringApi);
  protected readonly i18n = inject(I18n);

  readonly types = input.required<CatalogType[]>();
  protected readonly auto = signal(true);
  protected readonly snapshot = signal<Snapshot | null>(null);
  protected readonly loading = signal(false);
  private readonly reload$ = new Subject<void>();

  protected readonly heapPercent = computed(() => {
    const s = this.snapshot();
    return s?.heapUsed != null && s.heapMax ? (s.heapUsed / s.heapMax) * 100 : null;
  });
  /** Neither health nor uptime could be read: /actuator isn't reachable (not proxied, backend down, not ADMIN). */
  protected readonly unreachable = computed(() => {
    const s = this.snapshot();
    return !!s && s.health == null && s.uptimeSeconds == null;
  });
  protected readonly anyRequests = computed(() => this.snapshot()?.requests.some((r) => r.count > 0) ?? false);
  protected readonly totalRequests = computed(() => this.snapshot()?.requests.reduce((n, r) => n + r.count, 0) ?? 0);
  protected readonly cpuPercent = computed(() => {
    const cpu = this.snapshot()?.cpu;
    return cpu == null ? null : cpu * 100;
  });
  protected readonly poolPercent = computed(() => {
    const pool = this.snapshot()?.pool;
    return pool?.active != null && pool.max ? (pool.active / pool.max) * 100 : null;
  });
  protected readonly p95Slow = computed(() => (this.snapshot()?.traffic.p95Ms ?? 0) > P95_WARN_MS);

  private readonly history = signal<Sample[]>([]);
  protected readonly requestRates = computed(() => this.changes((s) => s.total, true));
  protected readonly p95Series = computed(() => this.history().map((s) => s.p95Ms ?? 0));
  protected readonly conflictSeries = computed(() => this.changes((s) => s.conflicts, false));
  protected readonly errorSeries = computed(() => this.changes((s) => s.serverErrors, false));
  protected readonly conflicts = computed(() => this.history().at(-1)?.conflicts ?? null);
  protected readonly serverErrors = computed(() => this.history().at(-1)?.serverErrors ?? null);
  /** Requests per second of each catalog between the last two readings. */
  protected readonly catalogRates = computed(() => {
    const [prev, last] = this.history().slice(-2);
    if (!prev || !last) return {} as Record<string, number>;
    const seconds = Math.max(1, (last.at - prev.at) / 1000);
    return Object.fromEntries(Object.entries(last.byCatalog).map(([type, n]) => [type, Math.max(0, n - (prev.byCatalog[type] ?? 0)) / seconds]));
  });
  protected readonly methods = computed(() => this.shares(this.snapshot()?.traffic.methods ?? []));
  protected readonly statuses = computed(() => (this.snapshot()?.traffic.statuses ?? []).slice().sort((a, b) => a.name.localeCompare(b.name)));

  constructor() {
    merge(timer(0, REFRESH_MS).pipe(filter(() => this.auto())), this.reload$)
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap(() => this.api.snapshot(this.types())),
        takeUntilDestroyed(),
      )
      .subscribe((snapshot) => {
        this.snapshot.set(snapshot);
        this.history.update((h) => [...h, sampleOf(snapshot)].slice(-HISTORY));
        this.loading.set(false);
      });
  }

  /** Change of a cumulative counter between consecutive readings (per second if asked); a restart's drop counts as 0. */
  private changes(pick: (s: Sample) => number, perSecond: boolean): number[] {
    const h = this.history();
    return h.slice(1).map((s, i) => Math.max(0, pick(s) - pick(h[i])) / (perSecond ? Math.max(1, (s.at - h[i].at) / 1000) : 1));
  }

  /** Biggest first, each with its share of the total in percent and its color. */
  private shares(tallies: Tally[]) {
    const total = tallies.reduce((n, t) => n + t.count, 0);
    return tallies
      .filter((t) => t.count > 0)
      .sort((a, b) => b.count - a.count)
      .map((t) => ({ ...t, color: METHOD_COLORS[t.name] ?? OTHER_METHOD_COLOR, percent: (t.count / total) * 100 }));
  }

  protected last(series: number[]) {
    return series.at(-1) ?? null;
  }

  protected statusClass(status: string) {
    return CONFLICTS.includes(status) ? 'warn' : status.startsWith('5') ? 'bad' : '';
  }

  protected reload() {
    this.reload$.next();
  }

  /** Empties one cache, or all of them when `ref` is omitted. */
  protected clear(ref?: CacheRef) {
    if (!ref && !confirm(this.i18n.t('confirmClearAll'))) return;
    this.api.clearCaches(ref).subscribe({ next: () => this.reload(), error: () => this.reload() });
  }

  /** Share of all catalog requests, in percent. */
  protected share(count: number) {
    const total = this.totalRequests();
    return total ? (count / total) * 100 : 0;
  }

  /** "p95 / p99", with "—" for the one that has no recent requests behind it. */
  protected pair(p95: number | null, p99: number | null) {
    if (p95 == null && p99 == null) return '—';
    return `${p95 == null ? '—' : this.ms(p95)} / ${p99 == null ? '—' : this.ms(p99)}`;
  }

  /** Color of an occupancy bar: amber from 75 %, red from 90 %. */
  protected level(percent: number | null) {
    return percent == null ? '' : percent >= 90 ? 'bad' : percent >= 75 ? 'warn' : '';
  }

  protected healthClass(status?: string) {
    return status === 'UP' ? 'ok' : status ? 'bad' : '';
  }

  protected hitRatio(c: CacheStats) {
    const total = (c.hits ?? 0) + (c.misses ?? 0);
    return total ? ((c.hits ?? 0) / total) * 100 : null;
  }

  protected num(value: number | null, digits = 0) {
    return value == null ? '—' : value.toLocaleString(this.i18n.lang(), { maximumFractionDigits: digits });
  }

  protected percent(value: number | null) {
    return value == null ? '—' : `${this.num(value, 1)} %`;
  }

  protected ms(value: number) {
    return `${this.num(value, 1)} ms`;
  }

  protected megabytes(bytes: number | null) {
    return bytes == null ? '—' : `${this.num(bytes / 1048576)} MB`;
  }

  protected duration(seconds: number | null) {
    if (seconds == null) return '—';
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return [d && `${d}d`, (d || h) && `${h}h`, `${m}m`].filter(Boolean).join(' ');
  }

  protected time(date: Date) {
    return date.toLocaleTimeString(this.i18n.lang());
  }
}
