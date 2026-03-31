import { inject, Injectable } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';

import { BaseHttpService } from '@core/services/http/base-http.service';

import { NotificationItem } from './notifications.models';

interface CollectionWrapper<T> {
  content: T[];
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(BaseHttpService);

  /** 多订阅方共享同一次请求（头部徽标 + HomeNotice 等） */
  private readonly listShared$ = this.http
    .get<CollectionWrapper<NotificationItem>>('/notifications', undefined, { showLoading: false })
    .pipe(
      map(body => body?.content ?? []),
      shareReplay({ bufferSize: 1, refCount: false })
    );

  /** 后端 {@code GET /notifications}，集合封装为 {@code content} */
  list(): Observable<NotificationItem[]> {
    return this.listShared$;
  }
}
