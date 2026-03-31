import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseHttpService } from '@core/services/http/base-http.service';

import { MonitorSnapshot } from './monitor.models';

@Injectable({ providedIn: 'root' })
export class MonitorService {
  private readonly http = inject(BaseHttpService);

  /** 拉取聚合监控快照；后端按 {@link MonitorContributor} 插件扩展模块。 */
  getSnapshot(): Observable<MonitorSnapshot> {
    return this.http.get<MonitorSnapshot>('/monitor/snapshot', undefined, { showLoading: false });
  }
}
