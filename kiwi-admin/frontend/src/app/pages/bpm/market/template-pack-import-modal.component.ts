import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzMessageService } from 'ng-zorro-antd/message';

@Component({
  selector: 'bpm-template-pack-import-modal',
  standalone: true,
  imports: [FormsModule, NzInputModule, NzButtonModule],
  template: `
    <div class="template-pack-import-dialog">
      <p class="template-pack-import-dialog__hint">上传 .kiwi-template-pack 文件，导入并创建新项目。</p>
      <div class="template-pack-import-dialog__field">
        <button type="button" nz-button (click)="fileInput.click()">选择文件</button>
        <input #fileInput type="file" accept=".kiwi-template-pack,.zip" hidden (change)="onFile($event)" />
        @if (fileName()) {
          <span class="m-l-8">{{ fileName() }}</span>
        }
      </div>
      <div class="template-pack-import-dialog__field">
        <div class="template-pack-import-dialog__label">新项目名称（可选）</div>
        <input nz-input [(ngModel)]="projectName" name="projectName" placeholder="默认使用包内名称" />
      </div>
    </div>
  `,
  styles: [
    `
      .template-pack-import-dialog__hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.45);
      }
      .template-pack-import-dialog__field {
        margin-bottom: 12px;
      }
    `
  ]
})
export class TemplatePackImportModalComponent {
  private readonly message = inject(NzMessageService);

  readonly fileName = signal('');
  file: File | null = null;
  projectName = '';

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0];
    if (f) {
      this.file = f;
      this.fileName.set(f.name);
    }
  }

  tryGetFormData(): FormData | null {
    if (!this.file) {
      this.message.warning('请选择模板包文件');
      return null;
    }
    const fd = new FormData();
    fd.append('file', this.file);
    const name = this.projectName?.trim();
    if (name) {
      fd.append('projectName', name);
    }
    return fd;
  }
}
