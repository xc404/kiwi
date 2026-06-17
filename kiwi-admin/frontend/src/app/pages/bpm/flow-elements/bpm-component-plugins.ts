import { Component, inject, OnInit, output, signal } from '@angular/core';
import { catchError, finalize, map, of } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTableModule } from 'ng-zorro-antd/table';

@Component({
  selector: 'app-bpm-component-plugins',
  standalone: true,
  imports: [NzCardModule, NzButtonModule, NzIconModule, NzTableModule, NzEmptyModule, NzSpinModule],
  template: `
    <nz-card class="bpm-component-plugins-card" nzTitle="组件插件">
      <p class="bpm-component-plugins-hint">
        将含 <code>@ComponentDescription</code> 的 JAR 上传至 <code>plugins/</code> 目录，无需修改 backend <code>pom.xml</code>。安装后组件来源为 <code>plugin</code>，会出现在设计器面板。
      </p>
      <div class="bpm-component-plugins-toolbar">
        <input #fileInput type="file" accept=".jar,application/java-archive" hidden (change)="onFileSelected($event)" />
        <button nz-button nzType="primary" [disabled]="loading()" [nzLoading]="uploading()" (click)="fileInput.click()">
          <nz-icon nzTheme="outline" nzType="upload" />
          上传 JAR
        </button>
        <button nz-button [disabled]="loading()" [nzLoading]="loading() && !uploading()" (click)="reload()">
          <nz-icon nzTheme="outline" nzType="reload" />
          重新扫描
        </button>
      </div>
      <nz-spin [nzSpinning]="loading()">
        @if (plugins().length) {
          <nz-table nzSize="small" [nzData]="plugins()" [nzFrontPagination]="false" [nzShowPagination]="false">
            <thead>
              <tr>
                <th>文件名</th>
                <th nzWidth="100px">操作</th>
              </tr>
            </thead>
            <tbody>
              @for (name of plugins(); track name) {
                <tr>
                  <td>{{ name }}</td>
                  <td>
                    <button type="button" nz-button nzDanger nzSize="small" [disabled]="loading()" (click)="confirmDelete(name)">卸载</button>
                  </td>
                </tr>
              }
            </tbody>
          </nz-table>
        } @else {
          <nz-empty nzNotFoundContent="暂无已安装插件" />
        }
      </nz-spin>
    </nz-card>
  `,
  styles: [
    `
      .bpm-component-plugins-card {
        margin-bottom: 16px;
      }
      .bpm-component-plugins-hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.65);
        font-size: 13px;
        line-height: 1.6;
      }
      .bpm-component-plugins-toolbar {
        display: flex;
        gap: 8px;
        margin-bottom: 12px;
      }
    `
  ]
})
export class BpmComponentPlugins implements OnInit {
  private readonly http = inject(BaseHttpService);
  private readonly message = inject(NzMessageService);
  private readonly modalWrap = inject(NzModalWrapService);

  readonly plugins = signal<string[]>([]);
  readonly loading = signal(false);
  readonly uploading = signal(false);

  /** 插件列表变更后通知父页刷新组件表 */
  readonly changed = output<void>();

  ngOnInit(): void {
    this.loadPlugins(false);
  }

  loadPlugins(showLoading = true): void {
    this.loading.set(showLoading);
    this.http
      .get<{ content?: string[] }>('/bpm/component/plugins', undefined, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`加载插件列表失败${err.message ?? ''}`);
          return of([] as string[]);
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe(list => this.plugins.set(list));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith('.jar')) {
      this.message.warning('仅支持 .jar 文件');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    this.uploading.set(true);
    this.loading.set(true);
    this.http
      .post<{ content?: string[] }>('/bpm/component/plugins/upload', formData, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`上传失败${err.message ?? ''}`);
          return of(null);
        }),
        finalize(() => {
          this.uploading.set(false);
          this.loading.set(false);
        })
      )
      .subscribe(list => {
        if (list == null) {
          return;
        }
        this.plugins.set(list);
        this.message.success(`已安装插件：${file.name}`);
        this.changed.emit();
      });
  }

  reload(): void {
    this.loading.set(true);
    this.http
      .post<{ content?: string[] }>('/bpm/component/plugins/reload', {}, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`重新扫描失败${err.message ?? ''}`);
          return of([] as string[]);
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe(list => {
        this.plugins.set(list);
        this.message.success('插件目录已重新扫描');
        this.changed.emit();
      });
  }

  confirmDelete(fileName: string): void {
    this.modalWrap.confirm({
      nzTitle: '卸载插件',
      nzContent: `确定卸载 ${fileName}？对应 plugin 来源组件将从组件库移除。`,
      nzOkText: '卸载',
      nzOkDanger: true,
      nzCancelText: '取消',
      nzOnOk: () => this.deletePlugin(fileName)
    });
  }

  private deletePlugin(fileName: string): void {
    this.loading.set(true);
    this.http
      .delete<{ content?: string[] }>(`/bpm/component/plugins/${encodeURIComponent(fileName)}`, undefined, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`卸载失败${err.message ?? ''}`);
          return of(null);
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe(list => {
        if (list == null) {
          return;
        }
        this.plugins.set(list);
        this.message.success(`已卸载 ${fileName}`);
        this.changed.emit();
      });
  }
}
