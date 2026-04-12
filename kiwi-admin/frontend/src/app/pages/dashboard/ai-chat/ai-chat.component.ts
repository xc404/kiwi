import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';

import { AiChatMessage, AiChatService } from '@services/ai-chat/ai-chat.service';
import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';

@Component({
  selector: 'app-ai-chat',
  templateUrl: './ai-chat.component.html',
  styleUrls: ['./ai-chat.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzBreadCrumbModule,
    NzCardModule,
    NzInputModule,
    NzButtonModule,
    NzIconModule,
    NzSpinModule,
    NzEmptyModule,
    FormsModule
  ]
})
export class AiChatComponent {
  private aiChat = inject(AiChatService);
  private message = inject(NzMessageService);
  private destroyRef = inject(DestroyRef);

  readonly scrollAnchor = viewChild<ElementRef<HTMLDivElement>>('scrollAnchor');

  messages = signal<AiChatMessage[]>([]);
  input = '';
  sending = signal(false);

  send(): void {
    const text = this.input.trim();
    if (!text || this.sending()) {
      return;
    }
    const history: AiChatMessage[] = [...this.messages(), { role: 'user', content: text }];
    this.messages.set(history);
    this.input = '';
    queueMicrotask(() => this.scrollToBottom());
    this.sending.set(true);

    this.aiChat
      .chat({ messages: history })
      .pipe(
        finalize(() => {
          this.sending.set(false);
          queueMicrotask(() => this.scrollToBottom());
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: res => {
          this.messages.update(list => [...list, { role: 'assistant', content: res.content ?? '' }]);
        },
        error: (err: { message?: string }) => {
          this.message.error(err?.message ?? '请求失败');
        }
      });
  }

  clear(): void {
    this.messages.set([]);
    this.input = '';
  }

  onInputKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this.send();
    }
  }

  private scrollToBottom(): void {
    const el = this.scrollAnchor()?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }
}
