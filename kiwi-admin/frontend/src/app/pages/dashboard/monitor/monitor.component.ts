import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, of, startWith, switchMap, tap } from 'rxjs';

import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzSpinModule } from "ng-zorro-antd/spin";
import { NzTagModule } from 'ng-zorro-antd/tag';

import { MonitorMetric, MonitorSnapshot } from './monitor.models';
import { MonitorService } from './monitor.service';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

const POLL_MS = 10_000;

@Component({
  selector: 'app-monitor',
  templateUrl: './monitor.component.html',
  styleUrls: ['./monitor.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzBreadCrumbModule,
    NzGridModule,
    NzButtonModule,
    NzSpinModule,
    NzProgressModule,
    NzTypographyModule,
    NzTagModule,
    DatePipe,
    DecimalPipe
  ]
})
export class MonitorComponent implements OnInit {
  private readonly monitor = inject(MonitorService);
  private readonly destroyRef = inject(DestroyRef);

  readonly snapshot = signal<MonitorSnapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    interval(POLL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.loadOnce()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
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
