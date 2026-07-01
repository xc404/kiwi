import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

export interface TemplatePackInstallModalData {
  packName: string;
  mode: 'newProject' | 'intoProject';
  targetProjectId?: string;
}

@Component({
  selector: 'bpm-template-pack-install-modal',
  standalone: true,
  imports: [FormsModule, NzInputModule],
  template: `
    <div class="template-pack-install-dialog">
      <p class="template-pack-install-dialog__hint">
        @if (mode === 'newProject') {
          将安装模板包「{{ packName }}」并创建新项目。
        } @else {
          将模板包「{{ packName }}」合并进当前项目（重名流程会自动加后缀）。
        }
      </p>
      @if (mode === 'newProject') {
        <div class="template-pack-install-dialog__field">
          <div class="template-pack-install-dialog__label">新项目名称</div>
          <input nz-input [(ngModel)]="projectName" name="projectName" />
        </div>
      }
    </div>
  `,
  styles: [
    `
      .template-pack-install-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
      }
      .template-pack-install-dialog__label {
        margin-bottom: 4px;
      }
    `
  ]
})
export class TemplatePackInstallModalComponent {
  private readonly message = inject(NzMessageService);
  private readonly nzData = inject<TemplatePackInstallModalData>(NZ_MODAL_DATA);

  readonly packName = this.nzData.packName;
  readonly mode = this.nzData.mode;
  projectName = this.nzData.packName;

  tryGetPayload(): { projectName?: string; targetProjectId?: string } | null {
    if (this.mode === 'newProject') {
      const projectName = this.projectName?.trim();
      if (!projectName) {
        this.message.warning('请填写项目名称');
        return null;
      }
      return { projectName };
    }
    if (!this.nzData.targetProjectId) {
      this.message.error('缺少目标项目');
      return null;
    }
    return { targetProjectId: this.nzData.targetProjectId };
  }
}
