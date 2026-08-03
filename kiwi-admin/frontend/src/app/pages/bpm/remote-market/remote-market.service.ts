import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';

export interface RemoteMarketItem {
  sourceId: string;
  sourceName?: string;
  type: 'template' | 'plugin';
  slug: string;
  name: string;
  version: string;
  summary?: string;
  category?: string;
  tags?: string[];
  kiwiMinVersion?: string;
  downloadUrl?: string;
  sha256?: string;
  manifestUrl?: string;
  kind?: string;
  processCount?: number;
  requiredComponentKeys?: string[];
  componentKeys?: string[];
  kiwiCompatible?: boolean;
  missingComponentKeys?: string[];
  mavenCoordinate?: { groupId?: string; artifactId?: string; version?: string };
}

export interface RemoteMarketItemDetail extends RemoteMarketItem {
  manifest?: Record<string, unknown>;
}

export interface RemoteMarketInstallResult {
  type: string;
  slug: string;
  version: string;
  projectId?: string;
  projectName?: string;
  pluginFileName?: string;
  installedComponentKeys?: string[];
}

export interface RemoteMarketSyncResult {
  sourceCount: number;
  itemCount: number;
  fetchedAt: number;
}

@Injectable({ providedIn: 'root' })
export class RemoteMarketService {
  private readonly http = inject(BaseHttpService);

  list(params?: { type?: string; keyword?: string; sourceId?: string }): Observable<RemoteMarketItem[]> {
    return this.http.get<RemoteMarketItem[]>('/bpm/remote-market', { params });
  }

  get(slug: string, version: string, sourceId?: string): Observable<RemoteMarketItemDetail> {
    const path = `/bpm/remote-market/${slug}/versions/${version}`;
    return this.http.get<RemoteMarketItemDetail>(path, sourceId ? { sourceId } : undefined);
  }

  sync(): Observable<RemoteMarketSyncResult> {
    return this.http.post<RemoteMarketSyncResult>('/bpm/remote-market/sync', {}, { needSuccessInfo: true });
  }

  installTemplate(
    slug: string,
    version: string,
    body?: { projectName?: string },
    sourceId?: string
  ): Observable<RemoteMarketInstallResult> {
    const path = `/bpm/remote-market/templates/${slug}/versions/${version}/install`;
    const url = sourceId ? `${path}?sourceId=${encodeURIComponent(sourceId)}` : path;
    return this.http.post<RemoteMarketInstallResult>(url, body ?? {}, { needSuccessInfo: true });
  }

  installPlugin(slug: string, version: string, sourceId?: string): Observable<RemoteMarketInstallResult> {
    const path = `/bpm/remote-market/plugins/${slug}/versions/${version}/install`;
    const url = sourceId ? `${path}?sourceId=${encodeURIComponent(sourceId)}` : path;
    return this.http.post<RemoteMarketInstallResult>(url, {}, { needSuccessInfo: true });
  }
}
