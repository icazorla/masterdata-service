import { KeyValuePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, filter, merge, switchMap, tap, timer } from 'rxjs';

import { I18n } from './i18n';
import { CatalogType } from './models';
import { CacheRef, CacheStats, MonitoringApi, Snapshot } from './monitoring-api';

const REFRESH_MS = 10_000;

/** Service health and metrics from Spring Boot Actuator, plus cache reset. */
@Component({
  selector: 'app-monitoring',
  imports: [FormsModule, KeyValuePipe],
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

  constructor() {
    merge(timer(0, REFRESH_MS).pipe(filter(() => this.auto())), this.reload$)
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap(() => this.api.snapshot(this.types())),
        takeUntilDestroyed(),
      )
      .subscribe((snapshot) => {
        this.snapshot.set(snapshot);
        this.loading.set(false);
      });
  }

  protected reload() {
    this.reload$.next();
  }

  /** Empties one cache, or all of them when `ref` is omitted. */
  protected clear(ref?: CacheRef) {
    if (!ref && !confirm(this.i18n.t('confirmClearAll'))) return;
    this.api.clearCaches(ref).subscribe({ next: () => this.reload(), error: () => this.reload() });
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
