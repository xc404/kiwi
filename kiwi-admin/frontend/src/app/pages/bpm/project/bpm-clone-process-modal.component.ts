import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

export interface BpmCloneProcessModalData {
  defaultName: string;
}

/** 克隆流程：输入新名称，由列表页 modal 的 nzOnOk 提交 */
@Component({
  selector: 'bpm-clone-process-modal',
  standalone: true,
  imports: [NzInputModule, FormsModule],
  template: `
    <div class="bpm-clone-process-dialog">
      <p class="bpm-clone-process-dialog__hint">将复制当前流程的 BPMN 定义，生成一条新的流程记录。</p>
      <div class="bpm-clone-process-dialog__field">
        <div class="bpm-clone-process-dialog__label">新流程名称</div>
        <input name="cloneProcessName" nz-input placeholder="请输入名称" [(ngModel)]="name" />
      </div>
    </div>
  `,
  styles: [
    `
      .bpm-clone-process-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
        font-size: 13px;
      }
      .bpm-clone-process-dialog__field {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .bpm-clone-process-dialog__label {
        font-size: 14px;
      }
    `
  ]
})
export class BpmCloneProcessModalComponent implements OnInit {
  private readonly message = inject(NzMessageService);
  private readonly nzData = inject<BpmCloneProcessModalData>(NZ_MODAL_DATA);

  name = '';

  ngOnInit(): void {
    this.name = this.nzData.defaultName;
  }

  tryGetName(): string | null {
    const name = this.name?.trim();
    if (!name) {
      this.message.warning('请填写新流程名称');
      return null;
    }
    return name;
  }
}
