import { Component, inject } from '@angular/core';

import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzListModule } from 'ng-zorro-antd/list';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

import { BpmComponentPluginDescriptor } from './bpm-component-plugin.types';

export interface BpmComponentPluginPreviewModalData {
  descriptor: BpmComponentPluginDescriptor;
  fileName: string;
}

@Component({
  selector: 'bpm-component-plugin-preview-modal',
  standalone: true,
  imports: [NzDescriptionsModule, NzListModule, NzTagModule],
  template: `
    <div class="plugin-preview-dialog">
      <p class="plugin-preview-dialog__hint">确认安装插件包「{{ descriptor.bundle?.name ?? fileName }}」？</p>
      <nz-descriptions nzBordered nzSize="small" class="m-b-12">
        <nz-descriptions-item nzTitle="包名">{{ descriptor.bundle?.name ?? '—' }}</nz-descriptions-item>
        <nz-descriptions-item nzTitle="版本">{{ descriptor.bundle?.version || '—' }}</nz-descriptions-item>
        <nz-descriptions-item nzTitle="简介">{{ descriptor.bundle?.summary || '—' }}</nz-descriptions-item>
        <nz-descriptions-item nzTitle="组件数">{{ descriptor.components?.length ?? 0 }}</nz-descriptions-item>
        <nz-descriptions-item nzTitle="文件名">{{ fileName }}</nz-descriptions-item>
      </nz-descriptions>
      @if (descriptor.warnings?.length) {
        <div class="plugin-preview-dialog__warnings m-b-12">
          @for (w of descriptor.warnings; track w) {
            <div>{{ w }}</div>
          }
        </div>
      }
      <h4>组件列表</h4>
      <nz-list nzBordered nzSize="small" [nzDataSource]="descriptor.components ?? []">
        <ng-template #renderItem let-item>
          <nz-list-item>
            <span>{{ item.name }}</span>
            <nz-tag>{{ item.key }}</nz-tag>
            @if (item.group) {
              <span class="text-muted">{{ item.group }}</span>
            }
            <span class="text-muted">{{ item.componentId }}</span>
            @if (item.source === 'scanned') {
              <nz-tag nzColor="orange">扫描</nz-tag>
            }
          </nz-list-item>
        </ng-template>
      </nz-list>
      @if (descriptor.bundle?.readme) {
        <h4 class="m-t-12">说明</h4>
        <pre class="plugin-preview-dialog__readme">{{ descriptor.bundle?.readme }}</pre>
      }
    </div>
  `,
  styles: [
    `
      .plugin-preview-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
      }
      .plugin-preview-dialog__warnings {
        color: #d48806;
        font-size: 13px;
      }
      .plugin-preview-dialog__readme {
        max-height: 200px;
        overflow: auto;
        margin: 0;
        padding: 8px;
        background: #fafafa;
        font-size: 12px;
        white-space: pre-wrap;
      }
      .text-muted {
        color: rgba(0, 0, 0, 0.45);
        font-size: 12px;
      }
      .m-b-12 {
        margin-bottom: 12px;
      }
      .m-t-12 {
        margin-top: 12px;
      }
    `
  ]
})
export class BpmComponentPluginPreviewModalComponent {
  private readonly nzData = inject<BpmComponentPluginPreviewModalData>(NZ_MODAL_DATA);

  readonly descriptor = this.nzData.descriptor;
  readonly fileName = this.nzData.fileName;
}
