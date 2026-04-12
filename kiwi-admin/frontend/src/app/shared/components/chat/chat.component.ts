import { NgClass } from '@angular/common';
import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ElementRef,
  OnDestroy,
  ChangeDetectorRef,
  inject,
  output,
  viewChild,
  computed,
  input,
  DestroyRef
} from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

import { Router } from '@angular/router';
import { ThemeService } from '@store/common-store/theme.service';
import { AiChatMessage, AiChatService } from '@services/ai-chat/ai-chat.service';

import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzResultModule } from 'ng-zorro-antd/result';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzTypographyModule,
    NzGridModule,
    NzAvatarModule,
    NzResultModule,
    NzIconModule,
    NzButtonModule,
    FormsModule,
    ReactiveFormsModule,
    NzInputModule,
    NgClass
  ]
})
export class ChatComponent implements OnInit, OnDestroy {
  readonly myScrollContainer = viewChild.required<ElementRef>('scrollMe');
  readonly changeShows = output<boolean>();
  /** 嵌入页面（如仪表盘路由）时为 true，不再使用右下角 fixed 布局 */
  readonly embed = input(false);

  validateForm!: FormGroup;
  messageArray: Array<{ msg: string; dir: 'left' | 'right'; isReaded: boolean }> = [];
  isSending = false;
  show = false;
  themeService = inject(ThemeService);
  private aiChat = inject(AiChatService);
  private router = inject(Router);
  private nzMessage = inject(NzMessageService);
  private destroyRef = inject(DestroyRef);

  readonly $themeStyle = computed(() => {
    return this.themeService.$themeStyle();
  });
  private fb = inject(FormBuilder);
  private cdr = inject(ChangeDetectorRef);

  ngOnDestroy(): void {
    console.log('客服功能销毁了');
  }

  close(): void {
    this.changeShows.emit(false);
  }

  scrollToBottom(): void {
    setTimeout(() => {
      try {
        this.myScrollContainer().nativeElement.scrollTop = this.myScrollContainer().nativeElement.scrollHeight;
      } catch (err) {
        console.error(err);
      }
    });
  }

  clearMsgInput(): void {
    setTimeout(() => {
      this.validateForm.get('question')?.reset();
    });
  }

  onTextareaKeydown(e: KeyboardEvent, value: string): void {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this.sendMessage(value, e);
    }
  }

  private buildAiMessages(): AiChatMessage[] {
    return this.messageArray.map(item => ({
      role: item.dir === 'right' ? 'user' : 'assistant',
      content: item.msg
    }));
  }

  sendMessage(msg: string, event: Event): void {
    if (!msg.trim()) {
      event.preventDefault();
      event.stopPropagation();
      this.clearMsgInput();
      return;
    }
    event.preventDefault();
    this.messageArray.push({ msg: msg.trim(), dir: 'right', isReaded: false });
    this.clearMsgInput();
    this.isSending = true;
    this.cdr.markForCheck();

    this.aiChat
      .assistant({ messages: this.buildAiMessages() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSending = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: res => {
          this.messageArray.forEach(item => {
            if (item.dir === 'right') {
              item.isReaded = true;
            }
          });
          this.messageArray.push({ msg: res.content ?? '', dir: 'left', isReaded: false });
          this.applyAssistantActions(res.actions);
          this.scrollToBottom();
          this.cdr.markForCheck();
        },
        error: (err: { message?: string }) => {
          this.nzMessage.error(err?.message ?? '请求失败');
          this.messageArray.pop();
          this.cdr.markForCheck();
        }
      });
  }

  ngOnInit(): void {
    this.validateForm = this.fb.group({
      question: [null]
    });
    this.scrollToBottom();
  }

  private applyAssistantActions(actions: { type: string; path?: string; queryParams?: Record<string, string> }[] | undefined): void {
    if (!actions?.length) {
      return;
    }
    for (const a of actions) {
      if (a.type === 'navigate' && a.path) {
        const raw = a.path.replace(/^\/+/, '');
        const segments = raw.split('/').filter(Boolean);
        this.router.navigate(segments, { queryParams: a.queryParams ?? {} }).catch(() => {
          this.nzMessage.warning('无法打开目标页面');
        });
        break;
      }
    }
  }
}
