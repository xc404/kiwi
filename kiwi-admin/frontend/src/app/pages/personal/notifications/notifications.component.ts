import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { catchError, of, take } from 'rxjs';

import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzListModule } from 'ng-zorro-antd/list';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

import { NotificationChannel, NotificationItem } from './notifications.models';
import { NotificationsService } from './notifications.service';

const CHANNELS: NotificationChannel[] = ['notice', 'message', 'todo'];
const TAB_TITLES = ['通知', '消息', '待办'];
const PAGE_SIZE = 8;

@Component({
  selector: 'app-notifications',
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzBreadCrumbModule,
    NzTabsModule,
    NzListModule,
    NzButtonModule,
    NzIconModule,
    NzTagModule,
    NzTypographyModule,
    NzSpinModule,
    NzEmptyModule,
    NzPaginationModule,
    NzPopconfirmModule,
    DatePipe
  ]
})
export class NotificationsComponent implements OnInit {
  private readonly api = inject(NotificationsService);
  private readonly message = inject(NzMessageService);
  readonly tabTitles = TAB_TITLES;
  readonly pageSize = PAGE_SIZE;

  readonly items = signal<NotificationItem[]>([]);
  readonly loading = signal(false);
  readonly tabIndex = signal(0);
  readonly pageIndex = signal(1);

  readonly currentChannel = computed(() => CHANNELS[this.tabIndex()]);

  readonly filtered = computed(() => {
    const ch = this.currentChannel();
    return this.items().filter(i => i.channel === ch);
  });

  readonly paged = computed(() => {
    const list = this.filtered();
    const start = (this.pageIndex() - 1) * PAGE_SIZE;
    return list.slice(start, start + PAGE_SIZE);
  });

  readonly totalFiltered = computed(() => this.filtered().length);

  tabLabel(i: number): string {
    const ch = CHANNELS[i];
    const n = this.items().filter(x => x.channel === ch).length;
    const u = this.items().filter(x => x.channel === ch && !x.read).length;
    return u > 0 ? `${TAB_TITLES[i]} (${n} · ${u} 未读)` : `${TAB_TITLES[i]} (${n})`;
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.api
      .list()
      .pipe(
        take(1),
        catchError(() => {
          this.message.error('加载失败');
          return of<NotificationItem[]>([]);
        })
      )
      .subscribe(list => {
        this.items.set(list);
        this.loading.set(false);
        this.clampPage();
      });
  }

  onTabChange(index: number): void {
    this.tabIndex.set(index);
    this.pageIndex.set(1);
  }

  onPageChange(page: number): void {
    this.pageIndex.set(page);
  }

  markRead(item: NotificationItem): void {
    if (item.read) {
      return;
    }
    this.items.update(arr => arr.map(x => (x.id === item.id ? { ...x, read: true } : x)));
  }

  markAllReadInChannel(): void {
    const ch = this.currentChannel();
    this.items.update(arr =>
      arr.map(x => (x.channel === ch ? { ...x, read: true } : x))
    );
    this.message.success('已全部标记为已读');
  }

  remove(item: NotificationItem): void {
    this.items.update(arr => arr.filter(x => x.id !== item.id));
    this.clampPage();
    this.message.success('已删除');
  }

  clearChannel(): void {
    const ch = this.currentChannel();
    this.items.update(arr => arr.filter(x => x.channel !== ch));
    this.pageIndex.set(1);
    this.message.success('已清空当前分类');
  }

  private clampPage(): void {
    const maxPage = Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE) || 1);
    if (this.pageIndex() > maxPage) {
      this.pageIndex.set(maxPage);
    }
  }
}
