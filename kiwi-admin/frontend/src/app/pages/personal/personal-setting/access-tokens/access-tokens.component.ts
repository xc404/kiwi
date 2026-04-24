import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { take } from 'rxjs';

import {
  CreatePersonalAccessTokenResult,
  PersonalAccessTokenRow,
  PersonalAccessTokenService
} from '@services/system/personal-access-token.service';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzWaveModule } from 'ng-zorro-antd/core/wave';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzTableModule } from 'ng-zorro-antd/table';

@Component({
  selector: 'app-access-tokens',
  templateUrl: './access-tokens.component.html',
  styleUrls: ['./access-tokens.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzAlertModule,
    NzInputModule,
    NzButtonModule,
    NzWaveModule,
    NzTableModule,
    NzPopconfirmModule,
    FormsModule,
    DatePipe
  ]
})
export class AccessTokensComponent implements OnInit {
  readonly data = input.required<{
    label: string;
  }>();

  private pat = inject(PersonalAccessTokenService);
  private msg = inject(NzMessageService);
  protected cdr = inject(ChangeDetectorRef);

  readonly rows = signal<PersonalAccessTokenRow[]>([]);
  readonly lastCreated = signal<CreatePersonalAccessTokenResult | null>(null);
  readonly creating = signal(false);

  draftName = '';

  ngOnInit(): void {
    this.loadList();
  }

  loadList(): void {
    this.pat
      .listMine()
      .pipe(take(1))
      .subscribe({
        next: list => {
          this.rows.set(list ?? []);
          this.cdr.markForCheck();
        },
        error: () => this.cdr.markForCheck()
      });
  }

  create(): void {
    this.creating.set(true);
    const name = this.draftName.trim() || undefined;
    this.pat
      .create({ name })
      .pipe(take(1))
      .subscribe({
        next: res => {
          this.lastCreated.set(res);
          this.draftName = '';
          this.loadList();
          this.msg.success('签发成功，请立即复制保存完整令牌');
        },
        error: () => {},
        complete: () => {
          this.creating.set(false);
          this.cdr.markForCheck();
        }
      });
  }

  dismissCreated(): void {
    this.lastCreated.set(null);
    this.cdr.markForCheck();
  }

  fullAuthorization(r: CreatePersonalAccessTokenResult): string {
    const t = (r.tokenType || 'Bearer').trim();
    return `${t} ${r.token}`.replace(/\s+/g, ' ').trim();
  }

  copy(text: string): void {
    void navigator.clipboard.writeText(text).then(
      () => this.msg.success('已复制'),
      () => this.msg.error('复制失败，请手动选择文本')
    );
  }

  revoke(row: PersonalAccessTokenRow): void {
    this.pat
      .revoke(row.id)
      .pipe(take(1))
      .subscribe({
        next: () => this.loadList(),
        error: () => this.cdr.markForCheck()
      });
  }
}
