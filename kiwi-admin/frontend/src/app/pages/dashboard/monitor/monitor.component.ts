import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, catchError, combineLatest, EMPTY, interval, of, switchMap, tap } from 'rxjs';

import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

import { MonitorMetric, MonitorSnapshot } from './monitor.models';
import { MonitorService } from './monitor.service';

const POLL_MS = 600_000;

@Component({
  selector: 'app-monitor',
  templateUrl: './monitor.component.html',
  styleUrls: ['./monitor.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NzCardModule, NzBreadCrumbModule, NzGridModule, NzButtonModule, NzSpinModule, NzProgressModule, NzTypographyModule, NzTagModule, NzSwitchModule, FormsModule, DatePipe, DecimalPipe]
})
export class MonitorComponent implements OnInit {
  private readonly monitor = inject(MonitorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pollActive$ = new BehaviorSubject(true);

  readonly snapshot = signal<MonitorSnapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly autoRefreshEnabled = signal(true);
  private readonly autoRefreshEnabled$ = toObservable(this.autoRefreshEnabled);

  ngOnInit(): void {
    this.pollActive$
      .pipe(
        switchMap(active => (active ? of(void 0) : EMPTY)),
        switchMap(() => this.loadOnce()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();

    combineLatest([this.pollActive$, this.autoRefreshEnabled$])
      .pipe(
        switchMap(([active, enabled]) => (active && enabled ? interval(POLL_MS) : EMPTY)),
        switchMap(() => this.loadOnce()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  setAutoRefresh(enabled: boolean): void {
    const wasEnabled = this.autoRefreshEnabled();
    this.autoRefreshEnabled.set(enabled);
    if (enabled && !wasEnabled) {
      this.refresh();
    }
  }

  /** 路由复用：离开监控页时暂停轮询，避免后台持续请求 /monitor/snapshot */
  _onReuseDestroy(): void {
    this.pollActive$.next(false);
  }

  /** 路由复用：回到监控页时恢复轮询 */
  _onReuseInit(): void {
    this.pollActive$.next(true);
  }

  refresh(): void {
    this.loadOnce().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  private loadOnce() {
    this.loading.set(true);
    this.error.set(null);
    return this.monitor.getSnapshot().pipe(
      tap(data => {
        this.snapshot.set(data);
        this.loading.set(false);
      }),
      catchError(err => {
        this.error.set(err?.message ?? '加载失败');
        this.loading.set(false);
        return of(null);
      })
    );
  }

  clampPercent(v: number | null | undefined): number {
    if (v == null || Number.isNaN(v)) {
      return 0;
    }
    return Math.min(100, Math.max(0, v));
  }

  formatBytes(value: number | null | undefined): string {
    if (value == null || value < 0 || Number.isNaN(value)) {
      return '—';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let n = value;
    let i = 0;
    while (n >= 1024 && i < units.length - 1) {
      n /= 1024;
      i++;
    }
    return `${n.toFixed(i === 0 ? 0 : 2)} ${units[i]}`;
  }

  boolOk(m: MonitorMetric): boolean {
    return (m.value ?? 0) > 0;
  }
}
