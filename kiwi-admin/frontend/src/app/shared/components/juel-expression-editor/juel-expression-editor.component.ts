import { CommonModule } from '@angular/common';
import {
  Component,
  effect,
  ElementRef,
  input,
  OnDestroy,
  output,
  signal,
  ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/bpm-spel-variable-context';
import { buildSpelVariableCompletion } from '@app/shared/components/spel-expression-editor/spel-expression-editor.component';
import { autocompletion, closeBrackets, CompletionContext, completionKeymap } from '@codemirror/autocomplete';
import { defaultKeymap, historyKeymap } from '@codemirror/commands';
import { javascript } from '@codemirror/lang-javascript';
import { EditorState, Extension } from '@codemirror/state';
import {
  placeholder as cmPlaceholder,
  EditorView,
  highlightActiveLine,
  keymap,
  lineNumbers,
} from '@codemirror/view';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzModalModule } from 'ng-zorro-antd/modal';

/** Camunda 使用 JUEL（统一表达式语言）；变量引用与 UI 补全仍采用 ${name}，与图中分析一致 */
export const JUEL_EXPRESSION_SNIPPETS: { label: string; insert: string }[] = [
  { label: 'execution', insert: '${execution}' },
  { label: '三元', insert: '${true ? a : b}' },
  { label: 'empty', insert: '${empty myVar}' },
  { label: 'eq', insert: "${myVar eq 'x'}" },
  { label: '数值比较', insert: '${amount gt 100}' },
];

@Component({
  selector: 'app-juel-expression-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NzButtonModule,
    NzIconModule,
    NzInputModule,
    NzDropdownModule,
    NzMenuModule,
    NzModalModule,
  ],
  templateUrl: './juel-expression-editor.component.html',
  styleUrl: './juel-expression-editor.component.css',
})
export class JuelExpressionEditorComponent implements OnDestroy {
  @ViewChild('modalHost') modalHostRef?: ElementRef<HTMLDivElement>;

  value = input<string | null | undefined>(null);
  readonly = input(false);
  variables = input<SpelVariableSuggestion[]>([]);
  placeholder = input('点击右侧图标编辑 JUEL');

  valueChange = output<string>();

  readonly snippets = JUEL_EXPRESSION_SNIPPETS;

  protected inlineText = signal('');
  protected modalVisible = signal(false);

  private modalEditorView?: EditorView;
  private syncingFromExternal = false;
  private endExternalSyncTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const next = this.value();
      const str = next == null ? '' : String(next);
      if (this.endExternalSyncTimer != null) {
        clearTimeout(this.endExternalSyncTimer);
      }
      this.syncingFromExternal = true;
      this.inlineText.set(str);
      // nz-input/ngModelChange 可能在微任务或下一 tick 才发出；若此处立即清标志，会用空串等脏值覆盖父级行数据
      this.endExternalSyncTimer = setTimeout(() => {
        this.syncingFromExternal = false;
        this.endExternalSyncTimer = undefined;
      }, 0);
    });
  }

  displayReadonlyValue(): string {
    const v = this.value();
    return v == null ? '' : String(v);
  }

  ngOnDestroy(): void {
    if (this.endExternalSyncTimer != null) {
      clearTimeout(this.endExternalSyncTimer);
    }
    this.destroyModalEditor();
  }

  onInlineChange(text: string): void {
    if (this.syncingFromExternal) {
      return;
    }
    this.inlineText.set(text);
    this.valueChange.emit(text);
  }

  openModal(): void {
    this.modalVisible.set(true);
  }

  onModalVisibleChange(visible: boolean): void {
    this.modalVisible.set(visible);
    if (!visible) {
      this.destroyModalEditor();
    }
  }

  onModalAfterOpen(): void {
    setTimeout(() => this.createModalEditor(), 0);
    setTimeout(() => {
      if (this.modalVisible() && !this.modalEditorView && !this.readonly()) {
        this.createModalEditor();
      }
    }, 50);
  }

  onModalOk(): void {
    if (!this.modalEditorView) {
      return;
    }
    const text = this.modalEditorView.state.doc.toString();
    this.inlineText.set(text);
    this.valueChange.emit(text);
    this.modalVisible.set(false);
  }

  insertVariableRef(v: SpelVariableSuggestion): void {
    this.insertText('${' + v.key + '}');
  }

  insertText(text: string): void {
    if (!this.modalEditorView || this.readonly()) {
      return;
    }
    const { from } = this.modalEditorView.state.selection.main;
    this.modalEditorView.dispatch({
      changes: { from, insert: text },
      selection: { anchor: from + text.length },
      scrollIntoView: true,
    });
    this.modalEditorView.focus();
  }

  private destroyModalEditor(): void {
    this.modalEditorView?.destroy();
    this.modalEditorView = undefined;
  }

  private createModalEditor(): void {
    const host = this.modalHostRef?.nativeElement;
    if (!host || this.readonly()) {
      return;
    }
    this.destroyModalEditor();
    const start = this.inlineText();
    const completionSource = buildSpelVariableCompletion(() => this.variables());

    const extensions: Extension[] = [
      lineNumbers(),
      highlightActiveLine(),
      closeBrackets(),
      javascript(),
      cmPlaceholder('JUEL（Camunda）；输入 $ 可插入 ${变量名}（与流程分析一致）'),
      autocompletion({
        override: [completionSource],
        activateOnTyping: true,
      }),
      keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap]),
      EditorView.lineWrapping,
      EditorView.theme({
        '&': { minHeight: '200px', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'monospace' },
      }),
      EditorState.allowMultipleSelections.of(false),
    ];

    this.modalEditorView = new EditorView({
      state: EditorState.create({
        doc: start,
        extensions,
      }),
      parent: host,
    });
  }
}
