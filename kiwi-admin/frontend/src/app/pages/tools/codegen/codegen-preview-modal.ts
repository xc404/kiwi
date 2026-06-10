import { Component, inject, OnInit, signal } from '@angular/core';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { Utils } from '@app/utils/utils';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzListModule } from 'ng-zorro-antd/list';
import { NZ_MODAL_DATA, NzModalFooterDirective, NzModalRef } from 'ng-zorro-antd/modal';
import { NzSpinModule } from 'ng-zorro-antd/spin';

export interface CodegenPreviewModalData {
  entityId: string;
  className?: string;
}

@Component({
  selector: 'codegen-preview-modal',
  standalone: true,
  imports: [NzListModule, NzSpinModule, NzButtonModule, NzIconModule, NzModalFooterDirective],
  template: `
    <nz-spin [nzSpinning]="loading()">
      <div class="codegen-preview-layout">
        <div class="codegen-preview-files">
          <nz-list nzSize="small" [nzDataSource]="filePaths()" [nzRenderItem]="fileItem">
            <ng-template #fileItem let-path>
              <nz-list-item
                class="codegen-preview-file-item"
                [class.codegen-preview-file-item--active]="path === selectedPath()"
                (click)="selectPath(path)"
              >
                {{ path }}
              </nz-list-item>
            </ng-template>
          </nz-list>
        </div>
        <pre class="codegen-preview-code">{{ selectedContent() }}</pre>
      </div>
    </nz-spin>
    <div *nzModalFooter>
      <button nz-button nzType="default" (click)="close()">关闭</button>
      <button nz-button nzType="primary" (click)="download()">
        <span nz-icon nzType="download"></span>
        下载 ZIP
      </button>
    </div>
  `,
  styles: [
    `
      .codegen-preview-layout {
        display: flex;
        gap: 12px;
        min-height: 60vh;
      }
      .codegen-preview-files {
        width: 280px;
        flex-shrink: 0;
        overflow: auto;
        max-height: 60vh;
        border-right: 1px solid #f0f0f0;
      }
      .codegen-preview-file-item {
        cursor: pointer;
        font-size: 12px;
        word-break: break-all;
      }
      .codegen-preview-file-item--active {
        background: #e6f4ff;
      }
      .codegen-preview-code {
        flex: 1;
        margin: 0;
        padding: 12px;
        overflow: auto;
        max-height: 60vh;
        font-size: 12px;
        line-height: 1.5;
        background: #fafafa;
        white-space: pre-wrap;
        word-break: break-word;
      }
    `
  ]
})
export class CodegenPreviewModalComponent implements OnInit {
  private readonly http = inject(BaseHttpService);
  private readonly modal = inject(NzModalRef);
  readonly nzModalData: CodegenPreviewModalData = inject(NZ_MODAL_DATA);

  loading = signal(true);
  files = signal<Record<string, string>>({});
  filePaths = signal<string[]>([]);
  selectedPath = signal('');
  selectedContent = signal('');

  ngOnInit(): void {
    const url = Utils.joinUrl('/tools/codegen', `entity/${this.nzModalData.entityId}/preview`);
    this.http.get<Record<string, string>>(url).subscribe({
      next: res => {
        const map = res ?? {};
        const paths = Object.keys(map).sort();
        this.files.set(map);
        this.filePaths.set(paths);
        if (paths.length > 0) {
          this.selectPath(paths[0]);
        }
      },
      complete: () => this.loading.set(false)
    });
  }

  selectPath(path: string): void {
    this.selectedPath.set(path);
    this.selectedContent.set(this.files()[path] ?? '');
  }

  close(): void {
    this.modal.close();
  }

  download(): void {
    this.modal.close({ download: true });
  }
}
