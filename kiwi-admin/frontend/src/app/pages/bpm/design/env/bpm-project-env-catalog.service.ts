import { inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';

import { BaseHttpService } from '@app/core/services/http/base-http.service';

import type { ProjectEnvVarMeta } from './project-env-var-meta';

interface BpmProjectEnvVarDto {
  key?: string | null;
  description?: string | null;
  encrypted?: boolean | null;
}

interface EnvPageResponse {
  content?: BpmProjectEnvVarDto[];
}

@Injectable({ providedIn: 'root' })
export class BpmProjectEnvCatalogService {
  private readonly http = inject(BaseHttpService);

  private readonly cache = new Map<string, ReadonlyMap<string, ProjectEnvVarMeta>>();
  private readonly inflight = new Map<string, Observable<ReadonlyMap<string, ProjectEnvVarMeta>>>();

  getCatalog(projectId: string | null | undefined): ReadonlyMap<string, ProjectEnvVarMeta> {
    if (!projectId) {
      return new Map();
    }
    return this.cache.get(projectId) ?? new Map();
  }

  /** 按 projectId 拉取环境变量 key 目录（带缓存） */
  ensureLoaded(projectId: string | null | undefined): void {
    if (!projectId || this.cache.has(projectId) || this.inflight.has(projectId)) {
      return;
    }
    this.loadCatalog(projectId).subscribe();
  }

  loadCatalog(projectId: string): Observable<ReadonlyMap<string, ProjectEnvVarMeta>> {
    const cached = this.cache.get(projectId);
    if (cached) {
      return of(cached);
    }
    const pending = this.inflight.get(projectId);
    if (pending) {
      return pending;
    }
    const req = this.http
      .get<EnvPageResponse>(`/bpm/project/${encodeURIComponent(projectId)}/env`, { page: 0, size: 500 }, { showLoading: false })
      .pipe(
        map(res => this.toCatalog(res?.content ?? [])),
        tap(map => {
          this.cache.set(projectId, map);
          this.inflight.delete(projectId);
        }),
        catchError(() => {
          this.inflight.delete(projectId);
          return of(new Map<string, ProjectEnvVarMeta>());
        }),
        shareReplay(1)
      );
    this.inflight.set(projectId, req);
    return req;
  }

  private toCatalog(items: BpmProjectEnvVarDto[]): ReadonlyMap<string, ProjectEnvVarMeta> {
    const map = new Map<string, ProjectEnvVarMeta>();
    for (const item of items) {
      const key = item.key?.trim();
      if (!key) {
        continue;
      }
      map.set(key, {
        key,
        description: item.description ?? null,
        encrypted: item.encrypted ?? null
      });
    }
    return map;
  }
}
