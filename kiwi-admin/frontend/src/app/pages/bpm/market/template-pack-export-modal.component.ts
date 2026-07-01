import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

export interface TemplatePackExportModalData {
  defaultName: string;
  projectId: string;
}

@Component({
  selector: 'bpm-template-pack-export-modal',
  standalone: true,
  imports: [FormsModule, NzInputModule, NzSelectModule],
  template: `
    <div class="template-pack-export-dialog">
      <p class="template-pack-export-dialog__hint">将当前项目下全部流程与环境变量发布为模板包。</p>
      <div class="template-pack-export-dialog__field">
        <div class="template-pack-export-dialog__label">模板包名称</div>
        <input nz-input [(ngModel)]="name" name="packName" />
      </div>
      <div class="template-pack-export-dialog__field">
        <div class="template-pack-export-dialog__label">摘要</div>
        <input nz-input [(ngModel)]="summary" name="summary" />
      </div>
      <div class="template-pack-export-dialog__field">
        <div class="template-pack-export-dialog__label">分类</div>
        <input nz-input [(ngModel)]="category" name="category" placeholder="如 ETL、CryoEMS" />
      </div>
      <div class="template-pack-export-dialog__field">
        <div class="template-pack-export-dialog__label">可见性</div>
        <nz-select [(ngModel)]="visibility" name="visibility" style="width: 100%">
          <nz-option nzLabel="团队可见" nzValue="Org" />
          <nz-option nzLabel="仅自己" nzValue="Private" />
        </nz-select>
      </div>
    </div>
  `,
  styles: [
    `
      .template-pack-export-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
        font-size: 13px;
      }
      .template-pack-export-dialog__field {
        margin-bottom: 12px;
      }
      .template-pack-export-dialog__label {
        margin-bottom: 4px;
        font-size: 14px;
      }
    `
  ]
})
export class TemplatePackExportModalComponent implements OnInit {
  private readonly message = inject(NzMessageService);
  private readonly nzData = inject<TemplatePackExportModalData>(NZ_MODAL_DATA);

  name = '';
  summary = '';
  category = '';
  visibility: 'Org' | 'Private' = 'Org';

  ngOnInit(): void {
    this.name = this.nzData.defaultName;
  }

  tryGetPayload(): { name: string; summary?: string; category?: string; visibility: string } | null {
    const name = this.name?.trim();
    if (!name) {
      this.message.warning('请填写模板包名称');
      return null;
    }
    return {
      name,
      summary: this.summary?.trim() || undefined,
      category: this.category?.trim() || undefined,
      visibility: this.visibility
    };
  }
}
