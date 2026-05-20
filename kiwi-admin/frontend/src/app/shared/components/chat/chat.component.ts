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
  DestroyRef,
  effect
} from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { from, of } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';

import { ThemeService } from '@store/common-store/theme.service';
import { AiChatMessage, AiChatService } from '@services/ai-chat/ai-chat.service';
import {
  AiChatConversation,
  AiConversationScope,
  AiConversationService
} from '@services/ai-chat/ai-conversation.service';
import { AssistantActionOrchestratorService } from '@shared/ai-assistant/assistant-action-orchestrator.service';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';

import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
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
    NzDropDownModule,
    NzMenuModule,
    NzPopconfirmModule,
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
  readonly embed = input(false);
  readonly defaultOpen = input(false);
  readonly actionHandlers = input<AssistantActionHandler[]>([]);
  readonly messagesEnricher = input<
    ((messages: AiChatMessage[]) => AiChatMessage[] | Promise<AiChatMessage[]>) | undefined
  >(undefined);
  readonly conversationScope = input<AiConversationScope>('global');
  readonly scopeRef = input<string | undefined>(undefined);

  validateForm!: FormGroup;
  messageArray: Array<{ msg: string; dir: 'left' | 'right'; isReaded: boolean }> = [];
  isSending = false;
  show = false;
  readonly conversationId = signal<string | null>(null);
  readonly conversationSummaries = signal<AiChatConversation[]>([]);
  readonly currentConversationTitle = signal('新会话');
  readonly sessionsLoading = signal(false);

  themeService = inject(ThemeService);
  private aiChat = inject(AiChatService);
  private aiConversation = inject(AiConversationService);
  private actionOrchestrator = inject(AssistantActionOrchestratorService);
  private nzMessage = inject(NzMessageService);
  private destroyRef = inject(DestroyRef);

  readonly $themeStyle = computed(() => this.themeService.$themeStyle());

  private fb = inject(FormBuilder);
  private cdr = inject(ChangeDetectorRef);
  private resizeActive = false;
  private resizeStartX = 0;
  private resizeStartY = 0;
  private resizeStartW = 0;
  private resizeStartH = 0;
  private resizeMoveListener: ((e: MouseEvent) => void) | null = null;
  private resizeUpListener: (() => void) | null = null;
  private scopeWatchInitialized = false;

  constructor() {
    effect(() => {
      this.conversationScope();
      this.scopeRef();
      if (!this.scopeWatchInitialized) {
        this.scopeWatchInitialized = true;
        this.reloadConversationList(true);
        return;
      }
      this.startNewConversation();
      this.reloadConversationList(false);
    });
  }

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
    this.panelWidth.set(Math.min(maxW, Math.max(CHAT_PANEL_MIN_WIDTH, this.resizeStartW + dw)));
    this.panelHeight.set(Math.min(maxH, Math.max(CHAT_PANEL_MIN_HEIGHT, this.resizeStartH + dh)));
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
        this.myScrollContainer().nativeElement.scrollTop =
          this.myScrollContainer().nativeElement.scrollHeight;
      } catch (err) {
        console.error(err);
      }
    });
  }

  clearMsgInput(): void {
    setTimeout(() => this.validateForm.get('question')?.reset());
  }

  onTextareaKeydown(e: KeyboardEvent, value: string): void {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this.sendMessage(value, e);
    }
  }

  startNewConversation(): void {
    this.conversationId.set(null);
    this.currentConversationTitle.set('新会话');
    this.messageArray = [];
    this.persistLastConversationId(null);
    this.cdr.markForCheck();
  }

  selectConversation(id: string): void {
    if (id === this.conversationId()) {
      return;
    }
    this.aiConversation
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: doc => {
          this.applyConversation(doc);
          this.cdr.markForCheck();
        },
        error: (err: { message?: string }) => {
          this.nzMessage.error(err?.message ?? '加载会话失败');
        }
      });
  }

  deleteCurrentConversation(): void {
    const id = this.conversationId();
    if (!id) {
      this.startNewConversation();
      return;
    }
    this.aiConversation
      .remove(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.nzMessage.success('已删除会话');
          this.startNewConversation();
          this.reloadConversationList(false);
        },
        error: (err: { message?: string }) => {
          this.nzMessage.error(err?.message ?? '删除失败');
        }
      });
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
    const userText = msg.trim();
    this.messageArray.push({ msg: userText, dir: 'right', isReaded: false });
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
          const assistantText = res.content ?? '';
          this.messageArray.push({ msg: assistantText, dir: 'left', isReaded: false });
          this.actionOrchestrator.dispatch(res.actions, this.actionHandlers());
          this.persistTurn(userText, assistantText);
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
    this.validateForm = this.fb.group({ question: [null] });
    this.scrollToBottom();
  }

  private persistTurn(userText: string, assistantText: string): void {
    const pair: AiChatMessage[] = [
      { role: 'user', content: userText },
      { role: 'assistant', content: assistantText }
    ];
    const id = this.conversationId();
    if (id) {
      this.aiConversation
        .update(id, { mode: 'append', messages: pair })
        .pipe(
          tap(doc => this.onConversationSaved(doc)),
          catchError(() => {
            this.nzMessage.warning('会话保存失败，刷新后可能丢失本轮对话');
            return of(null);
          }),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe();
      return;
    }
    this.aiConversation
      .create({
        scope: this.conversationScope(),
        scopeRef: this.scopeRef(),
        messages: pair
      })
      .pipe(
        tap(doc => {
          if (doc?.id) {
            this.onConversationSaved(doc);
            this.reloadConversationList(false);
          }
        }),
        catchError(() => {
          this.nzMessage.warning('会话保存失败，刷新后可能丢失本轮对话');
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  private onConversationSaved(doc: AiChatConversation): void {
    if (!doc?.id) {
      return;
    }
    this.conversationId.set(doc.id);
    this.currentConversationTitle.set(doc.title ?? '新会话');
    this.persistLastConversationId(doc.id);
    this.upsertSummary(doc);
  }

  private upsertSummary(doc: AiChatConversation): void {
    const list = [...this.conversationSummaries()];
    const ix = list.findIndex(c => c.id === doc.id);
    const summary: AiChatConversation = {
      id: doc.id,
      title: doc.title,
      lastMessagePreview: doc.lastMessagePreview,
      updatedTime: doc.updatedTime,
      messageCount: doc.messageCount
    };
    if (ix >= 0) {
      list[ix] = summary;
    } else {
      list.unshift(summary);
    }
    this.conversationSummaries.set(list);
  }

  private reloadConversationList(restoreLast: boolean): void {
    this.sessionsLoading.set(true);
    const scopeRef = this.scopeRef();
    this.aiConversation
      .list({
        scope: this.conversationScope(),
        scopeRef: scopeRef ?? '',
        page: 0,
        size: 50
      })
      .pipe(
        finalize(() => {
          this.sessionsLoading.set(false);
          this.cdr.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: page => {
          const items = page?.content ?? [];
          this.conversationSummaries.set(items);
          if (restoreLast) {
            this.tryRestoreLastConversation(items);
          }
          this.cdr.markForCheck();
        },
        error: () => this.conversationSummaries.set([])
      });
  }

  private tryRestoreLastConversation(summaries: AiChatConversation[]): void {
    const lastId = this.readLastConversationId();
    if (!lastId) {
      return;
    }
    if (summaries.find(s => s.id === lastId)) {
      this.selectConversation(lastId);
      return;
    }
    this.aiConversation
      .get(lastId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: doc => this.applyConversation(doc),
        error: () => this.persistLastConversationId(null)
      });
  }

  private applyConversation(doc: AiChatConversation): void {
    this.conversationId.set(doc.id ?? null);
    this.currentConversationTitle.set(doc.title ?? '新会话');
    this.persistLastConversationId(doc.id ?? null);
    this.messageArray = (doc.messages ?? []).map(m => ({
      msg: m.content ?? '',
      dir: m.role === 'user' ? 'right' : 'left',
      isReaded: m.role === 'user'
    }));
    this.scrollToBottom();
  }

  private lastConversationStorageKey(): string {
    const scope = this.conversationScope();
    const ref = this.scopeRef() ?? '';
    return `kiwi.ai.conversation.last.${scope}.${ref}`;
  }

  private persistLastConversationId(id: string | null): void {
    try {
      const key = this.lastConversationStorageKey();
      if (id) {
        localStorage.setItem(key, id);
      } else {
        localStorage.removeItem(key);
      }
    } catch {
      /* ignore */
    }
  }

  private readLastConversationId(): string | null {
    try {
      return localStorage.getItem(this.lastConversationStorageKey());
    } catch {
      return null;
    }
  }
}
