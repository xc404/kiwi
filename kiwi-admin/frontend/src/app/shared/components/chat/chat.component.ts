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
  signal,
  DestroyRef
} from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { from, of } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';

import { ThemeService } from '@store/common-store/theme.service';
import { AiChatMessage, AiChatService } from '@services/ai-chat/ai-chat.service';
import { AssistantActionOrchestratorService } from '@shared/ai-assistant/assistant-action-orchestrator.service';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';

import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzResultModule } from 'ng-zorro-antd/result';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

const CHAT_PANEL_MIN_WIDTH = 300;
const CHAT_PANEL_MIN_HEIGHT = 380;
const CHAT_PANEL_MAX_WIDTH = 960;
const CHAT_PANEL_DEFAULT_WIDTH = 420;
const CHAT_PANEL_DEFAULT_HEIGHT = 560;

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
  readonly panelWidth = signal(CHAT_PANEL_DEFAULT_WIDTH);
  readonly panelHeight = signal(CHAT_PANEL_DEFAULT_HEIGHT);
  /** 嵌入页面（如仪表盘路由）时为 true，不再使用右下角 fixed 布局 */
  readonly embed = input(false);
  /** 浮动模式初始是否展开（如 BPM 设计器无全局入口，宜默认展开） */
  readonly defaultOpen = input(false);
  /** 每嵌入点可选的额外动作处理器（与内置 navigate 等合并编排） */
  readonly actionHandlers = input<AssistantActionHandler[]>([]);
  /** 发送前合并上下文（如 BPM 设计器注入 processId / BPMN XML） */
  readonly messagesEnricher = input<
    ((messages: AiChatMessage[]) => AiChatMessage[] | Promise<AiChatMessage[]>) | undefined
  >(undefined);

  validateForm!: FormGroup;
  messageArray: Array<{ msg: string; dir: 'left' | 'right'; isReaded: boolean }> = [];
  isSending = false;
  show = false;
  themeService = inject(ThemeService);
  private aiChat = inject(AiChatService);
  private actionOrchestrator = inject(AssistantActionOrchestratorService);
  private nzMessage = inject(NzMessageService);
  private destroyRef = inject(DestroyRef);

  readonly $themeStyle = computed(() => {
    return this.themeService.$themeStyle();
  });
  private fb = inject(FormBuilder);
  private cdr = inject(ChangeDetectorRef);
  private resizeActive = false;
  private resizeStartX = 0;
  private resizeStartY = 0;
  private resizeStartW = 0;
  private resizeStartH = 0;
  private resizeMoveListener: ((e: MouseEvent) => void) | null = null;
  private resizeUpListener: (() => void) | null = null;

  ngOnDestroy(): void {
    this.teardownResizeListeners();
  }

  onResizeStart(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.teardownResizeListeners();
    this.resizeActive = true;
    this.resizeStartX = event.clientX;
    this.resizeStartY = event.clientY;
    this.resizeStartW = this.panelWidth();
    this.resizeStartH = this.panelHeight();

    this.resizeMoveListener = (e: MouseEvent) => this.onResizeMove(e);
    this.resizeUpListener = () => this.onResizeEnd();
    document.addEventListener('mousemove', this.resizeMoveListener);
    document.addEventListener('mouseup', this.resizeUpListener);
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'nwse-resize';
  }

  private onResizeMove(event: MouseEvent): void {
    if (!this.resizeActive) {
      return;
    }
    const dw = this.resizeStartX - event.clientX;
    const dh = this.resizeStartY - event.clientY;
    const maxW = Math.min(window.innerWidth * 0.9, CHAT_PANEL_MAX_WIDTH);
    const maxH = window.innerHeight * 0.9;
    this.panelWidth.set(
      Math.min(maxW, Math.max(CHAT_PANEL_MIN_WIDTH, this.resizeStartW + dw)),
    );
    this.panelHeight.set(
      Math.min(maxH, Math.max(CHAT_PANEL_MIN_HEIGHT, this.resizeStartH + dh)),
    );
    this.cdr.markForCheck();
  }

  private onResizeEnd(): void {
    this.resizeActive = false;
    this.teardownResizeListeners();
    this.cdr.markForCheck();
  }

  private teardownResizeListeners(): void {
    if (this.resizeMoveListener) {
      document.removeEventListener('mousemove', this.resizeMoveListener);
      this.resizeMoveListener = null;
    }
    if (this.resizeUpListener) {
      document.removeEventListener('mouseup', this.resizeUpListener);
      this.resizeUpListener = null;
    }
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
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

    from(Promise.resolve(this.buildAiMessages()))
      .pipe(
        switchMap(msgs => {
          const enricher = this.messagesEnricher();
          if (!enricher) {
            return of(msgs);
          }
          return from(Promise.resolve(enricher(msgs)));
        }),
        switchMap(messages => this.aiChat.assistant({ messages })),
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
          this.actionOrchestrator.dispatch(res.actions, this.actionHandlers());
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
    if (!this.embed() && this.defaultOpen()) {
      this.show = true;
    }
    this.validateForm = this.fb.group({
      question: [null]
    });
    this.scrollToBottom();
  }
}
