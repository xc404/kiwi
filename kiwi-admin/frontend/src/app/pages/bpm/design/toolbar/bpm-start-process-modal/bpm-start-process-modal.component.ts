import { Component, inject, viewChild } from '@angular/core';
import { JsonCodeEditorComponent } from '@app/shared/components/json-code-editor/json-code-editor.component';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';
import { NzMessageService } from 'ng-zorro-antd/message';

export interface BpmStartProcessModalData {
  initialText: string;
}

/** 启动流程变量：仅 modal 内容区，由 NzModalWrapService 承载外壳与按钮 */
@Component({
  selector: 'bpm-start-process-modal',
  standalone: true,
  imports: [JsonCodeEditorComponent],
  templateUrl: './bpm-start-process-modal.component.html',
  styleUrl: './bpm-start-process-modal.component.scss',
})
export class BpmStartProcessModalComponent {
  private readonly message = inject(NzMessageService);
  readonly nzData = inject<BpmStartProcessModalData>(NZ_MODAL_DATA);

  private readonly jsonEditor = viewChild(JsonCodeEditorComponent);

  /** 解析成功返回变量对象；失败返回 false（用于 nzOnOk 阻止关闭） */
  parseVariablesOrFalse(): Record<string, unknown> | false {
    const raw = this.jsonEditor()?.getDocumentText() ?? this.nzData.initialText ?? '';
    const trimmed = raw.trim();
    try {
      const rawParsed = trimmed === '' ? {} : JSON.parse(trimmed);
      if (
        rawParsed === null ||
        typeof rawParsed !== 'object' ||
        Array.isArray(rawParsed)
      ) {
        this.message.warning('须为 JSON 对象，例如 {} 或 {"key":"value"}');
        return false;
      }
      return rawParsed as Record<string, unknown>;
    } catch {
      this.message.error('JSON 格式无效');
      return false;
    }
  }
}
