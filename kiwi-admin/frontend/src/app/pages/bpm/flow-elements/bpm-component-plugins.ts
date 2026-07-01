import { Component, inject, OnInit, output, signal } from '@angular/core';
import { catchError, finalize, map, of, switchMap } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { BpmComponentPluginPreviewModalComponent } from './bpm-component-plugin-preview-modal.component';
import { BpmComponentPluginDescriptor } from './bpm-component-plugin.types';

@Component({
  selector: 'app-bpm-component-plugins',
  standalone: true,
  imports: [NzCardModule, NzButtonModule, NzIconModule, NzTableModule, NzEmptyModule, NzSpinModule, NzTagModule],
  template: `
    <nz-card class="bpm-component-plugins-card" nzTitle="组件插件">
      <p class="bpm-component-plugins-hint">
        将含 <code>@ComponentDescription</code> 的 JAR 上传至 <code>plugins/</code> 目录，无需修改 backend <code>pom.xml</code>。可选内嵌
        <code>META-INF/kiwi/component-bundle.json</code> 提供包名、版本与组件清单；安装后组件来源为 <code>plugin</code>。
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
                <th nzWidth="40px"></th>
                <th>包名</th>
                <th nzWidth="90px">版本</th>
                <th>简介</th>
                <th nzWidth="72px">组件数</th>
                <th>文件名</th>
                <th nzWidth="100px">操作</th>
              </tr>
            </thead>
            <tbody>
              @for (row of plugins(); track row.fileName) {
                <tr>
                  <td
                    nzExpand
                    [nzExpand]="isExpanded(row.fileName)"
                    (nzExpandChange)="toggleExpand(row.fileName, $event)"
                  ></td>
                  <td>{{ row.bundle?.name ?? row.fileName }}</td>
                  <td>{{ row.bundle?.version || '—' }}</td>
                  <td>{{ row.bundle?.summary || '—' }}</td>
                  <td>{{ row.components?.length ?? 0 }}</td>
                  <td>{{ row.fileName }}</td>
                  <td>
                    <button type="button" nz-button nzDanger nzSize="small" [disabled]="loading()" (click)="confirmDelete(row.fileName)">
                      卸载
                    </button>
                  </td>
                </tr>
                <tr [nzExpand]="isExpanded(row.fileName)">
                  <td colspan="7" class="bpm-component-plugins-expand">
                    @if (row.warnings?.length) {
                      <div class="bpm-component-plugins-warnings">
                        @for (w of row.warnings; track w) {
                          <div>{{ w }}</div>
                        }
                      </div>
                    }
                    <table class="bpm-component-plugins-subtable">
                      <thead>
                        <tr>
                          <th>名称</th>
                          <th>key</th>
                          <th>分组</th>
                          <th>componentId</th>
                          <th>来源</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (c of row.components ?? []; track c.componentId) {
                          <tr>
                            <td>{{ c.name }}</td>
                            <td><code>{{ c.key }}</code></td>
                            <td>{{ c.group || '—' }}</td>
                            <td><code>{{ c.componentId }}</code></td>
                            <td>
                              @if (c.source === 'scanned') {
                                <nz-tag nzColor="orange">扫描</nz-tag>
                              } @else {
                                <nz-tag nzColor="blue">清单</nz-tag>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                    @if (row.bundle?.readme) {
                      <pre class="bpm-component-plugins-readme">{{ row.bundle?.readme }}</pre>
                    }
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
      .bpm-component-plugins-expand {
        background: #fafafa;
        padding: 8px 12px;
      }
      .bpm-component-plugins-warnings {
        color: #d48806;
        font-size: 12px;
        margin-bottom: 8px;
      }
      .bpm-component-plugins-subtable {
        width: 100%;
        border-collapse: collapse;
        font-size: 13px;
      }
      .bpm-component-plugins-subtable th,
      .bpm-component-plugins-subtable td {
        border: 1px solid #f0f0f0;
        padding: 4px 8px;
        text-align: left;
      }
      .bpm-component-plugins-readme {
        margin: 8px 0 0;
        max-height: 160px;
        overflow: auto;
        font-size: 12px;
        white-space: pre-wrap;
        background: #fff;
        padding: 8px;
        border: 1px solid #f0f0f0;
      }
    `
  ]
})
export class BpmComponentPlugins implements OnInit {
  private readonly http = inject(BaseHttpService);
  private readonly message = inject(NzMessageService);
  private readonly modalWrap = inject(NzModalWrapService);

  readonly plugins = signal<BpmComponentPluginDescriptor[]>([]);
  readonly loading = signal(false);
  readonly uploading = signal(false);
  private readonly expanded = signal<Record<string, boolean>>({});

  /** 插件列表变更后通知父页刷新组件表 */
  readonly changed = output<void>();

  ngOnInit(): void {
    this.loadPlugins(false);
  }

  isExpanded(fileName: string): boolean {
    return !!this.expanded()[fileName];
  }

  toggleExpand(fileName: string, expanded: boolean): void {
    this.expanded.update(map => ({ ...map, [fileName]: expanded }));
  }

  loadPlugins(showLoading = true): void {
    this.loading.set(showLoading);
    this.http
      .get<{ content?: BpmComponentPluginDescriptor[] }>('/bpm/component/plugins', undefined, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`加载插件列表失败${err.message ?? ''}`);
          return of([] as BpmComponentPluginDescriptor[]);
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
      .post<{ content?: BpmComponentPluginDescriptor }>('/bpm/component/plugins/preview', formData, { showLoading: false })
      .pipe(
        switchMap(res => {
          const descriptor = res?.content;
          if (!descriptor) {
            return of(null);
          }
          return new Promise<BpmComponentPluginDescriptor | null>(resolve => {
            this.modalWrap.confirm({
              nzTitle: '确认安装插件',
              nzWidth: 640,
              nzContent: BpmComponentPluginPreviewModalComponent,
              nzData: { descriptor, fileName: file.name },
              nzOkText: '安装',
              nzCancelText: '取消',
              nzOnOk: () => resolve(descriptor),
              nzOnCancel: () => resolve(null)
            });
          });
        }),
        switchMap(confirmed => {
          if (!confirmed) {
            return of(null);
          }
          const uploadData = new FormData();
          uploadData.append('file', file);
          return this.http.post<{ content?: BpmComponentPluginDescriptor[] }>('/bpm/component/plugins/upload', uploadData, {
            showLoading: false
          });
        }),
        map(res => (res == null ? null : (res?.content ?? []))),
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
      .post<{ content?: BpmComponentPluginDescriptor[] }>('/bpm/component/plugins/reload', {}, { showLoading: false })
      .pipe(
        map(res => res?.content ?? []),
        catchError(err => {
          this.message.error(`重新扫描失败${err.message ?? ''}`);
          return of([] as BpmComponentPluginDescriptor[]);
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
      .delete<{ content?: BpmComponentPluginDescriptor[] }>(`/bpm/component/plugins/${encodeURIComponent(fileName)}`, undefined, {
        showLoading: false
      })
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
