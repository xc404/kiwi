import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of, take } from 'rxjs';

import { NotificationChannel, NotificationItem } from '@app/pages/personal/notifications/notifications.models';
import { NotificationsService } from '@app/pages/personal/notifications/notifications.service';

import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzListModule } from 'ng-zorro-antd/list';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

const PREVIEW_LIMIT = 5;

@Component({
  selector: 'app-home-notice',
  templateUrl: './home-notice.component.html',
  styleUrls: ['./home-notice.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzTabsModule,
    NzListModule,
    NzTypographyModule,
    NzTagModule,
    RouterLink,
    NzSpinModule,
    NzEmptyModule,
    DatePipe
  ]
})
export class HomeNoticeComponent implements OnInit {
  private readonly notifications = inject(NotificationsService);

  readonly channels: NotificationChannel[] = ['notice', 'message', 'todo'];
  readonly tabLabels = ['通知', '消息', '待办'];

  readonly items = signal<NotificationItem[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.notifications
      .list()
      .pipe(
        take(1),
        catchError(() => of<NotificationItem[]>([]))
      )
      .subscribe(items => {
        this.items.set(items);
        this.loading.set(false);
      });
  }

  tabTitle(i: number): string {
    const ch = this.channels[i];
    return `${this.tabLabels[i]}(${this.count(ch)})`;
  }

  count(ch: NotificationChannel): number {
    return this.items().filter(x => x.channel === ch).length;
  }

  /** 下拉内每类最多展示前几条 */
  forChannel(ch: NotificationChannel): NotificationItem[] {
    return this.items()
      .filter(x => x.channel === ch)
      .slice(0, PREVIEW_LIMIT);
  }
}
