import { DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import type { BpmProcess } from '../../../types/bpm-process';

@Component({
  selector: 'bpm-editor-process-meta',
  standalone: true,
  imports: [DatePipe, NzSpinModule, NzButtonModule, NzIconModule],
  templateUrl: './bpm-editor-process-meta.component.html',
  styleUrl: './bpm-editor-process-meta.component.scss',
})
export class BpmEditorProcessMetaComponent {
  loading = input(false);
  meta = input<BpmProcess | null>(null);

  private readonly message = inject(NzMessageService);

  copyProcessId(id: string): void {
    if (!id) {
      return;
    }
    const ok = () => this.message.success('已复制到剪贴板');
    const fail = () => this.message.error('复制失败');
    if (navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(id).then(ok).catch(() => {
        try {
          const ta = document.createElement('textarea');
          ta.value = id;
          ta.style.position = 'fixed';
          ta.style.left = '-9999px';
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
          ok();
        } catch {
          fail();
        }
      });
    } else {
      try {
        const ta = document.createElement('textarea');
        ta.value = id;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        ok();
      } catch {
        fail();
      }
    }
  }
}
