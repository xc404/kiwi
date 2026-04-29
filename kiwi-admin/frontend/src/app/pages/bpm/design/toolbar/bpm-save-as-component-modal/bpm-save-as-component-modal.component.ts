import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';
import { NzMessageService } from 'ng-zorro-antd/message';

export type SaveAsComponentFormPayload = {
  name: string;
  description?: string;
  version?: string;
};

export interface BpmSaveAsComponentModalData {
  defaultName: string;
  defaultDescription: string;
  defaultVersion: string;
}

/** 另存为组件：仅 modal 内容区，由 NzModalWrapService 承载外壳与按钮 */
@Component({
  selector: 'bpm-save-as-component-modal',
  standalone: true,
  imports: [NzInputModule, FormsModule],
  templateUrl: './bpm-save-as-component-modal.component.html',
  styleUrl: './bpm-save-as-component-modal.component.scss',
})
export class BpmSaveAsComponentModalComponent implements OnInit {
  private readonly message = inject(NzMessageService);
  private readonly nzData = inject<BpmSaveAsComponentModalData>(NZ_MODAL_DATA);

  name = '';
  description = '';
  version = '1.0';

  ngOnInit(): void {
    this.name = this.nzData.defaultName;
    this.description = this.nzData.defaultDescription;
    this.version = this.nzData.defaultVersion;
  }

  /** 校验通过返回表单数据；失败时提示并返回 null（用于 nzOnOk 阻止关闭） */
  tryGetPayload(): SaveAsComponentFormPayload | null {
    const name = this.name?.trim();
    const description = this.description?.trim();
    const version = this.version?.trim();
    if (!name) {
      this.message.warning('请填写 name（组件名称）');
      return null;
    }
    return {
      name,
      description: description || undefined,
      version: version || undefined,
    };
  }
}
