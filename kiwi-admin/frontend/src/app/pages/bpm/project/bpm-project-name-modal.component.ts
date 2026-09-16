import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

export interface BpmProjectNameModalData {
  hint: string;
  label: string;
  defaultName: string;
  emptyWarning: string;
}

/** 新建/重命名项目：输入名称，由调用方 modal 的 nzOnOk 提交 */
@Component({
  selector: 'bpm-project-name-modal',
  standalone: true,
  imports: [NzInputModule, FormsModule],
  template: `
    <div class="bpm-project-name-dialog">
      <p class="bpm-project-name-dialog__hint">{{ nzData.hint }}</p>
      <div class="bpm-project-name-dialog__field">
        <div class="bpm-project-name-dialog__label">{{ nzData.label }}</div>
        <input name="projectName" nz-input placeholder="请输入名称" [(ngModel)]="name" />
      </div>
    </div>
  `,
  styles: [
    `
      .bpm-project-name-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
        font-size: 13px;
      }
      .bpm-project-name-dialog__field {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .bpm-project-name-dialog__label {
        font-size: 14px;
      }
    `
  ]
})
export class BpmProjectNameModalComponent implements OnInit {
  private readonly message = inject(NzMessageService);
  readonly nzData = inject<BpmProjectNameModalData>(NZ_MODAL_DATA);

  name = '';

  ngOnInit(): void {
    this.name = this.nzData.defaultName;
  }

  tryGetName(): string | null {
    const name = this.name?.trim();
    if (!name) {
      this.message.warning(this.nzData.emptyWarning);
      return null;
    }
    return name;
  }
}
