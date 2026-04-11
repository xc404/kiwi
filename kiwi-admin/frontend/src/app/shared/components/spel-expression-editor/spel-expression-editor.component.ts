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

export const SPEL_EXPRESSION_SNIPPETS: { label: string; insert: string }[] = [
  { label: '#root', insert: '#root' },
  { label: '三元', insert: '(true ? a : b)' },
  { label: 'String.format', insert: "T(java.lang.String).format('%s', #x)" },
  { label: 'Math.max', insert: 'T(java.lang.Math).max(#a, #b)' },
  { label: '非空', insert: '#x != null ? #x : \'\'' },
  { label: '字符串连接', insert: '#a + #b' },
];

export function buildSpelVariableCompletion(getVariables: () => SpelVariableSuggestion[]) {
  return (context: CompletionContext) => {
    const variables = getVariables();
    const beforeBrace = context.matchBefore(/\$\{[a-zA-Z0-9_]*$/);
    if (beforeBrace) {
      const partial = beforeBrace.text.slice(2);
      const from = beforeBrace.from + 2;
      return {
        from,
        options: variables
          .filter((v) => !partial || v.key.startsWith(partial))
          .map((v) => ({
            label: v.key,
            detail: v.source === 'upstream' ? '上游输出' : '图中引用',
            apply: v.key,
            type: 'variable',
          })),
        filter: true,
      };
    }

    const beforeDollarWord = context.matchBefore(/\$[a-zA-Z0-9_]*$/);
    if (beforeDollarWord && !beforeDollarWord.text.includes('{')) {
      const partial = beforeDollarWord.text.slice(1);
      const from = beforeDollarWord.from;
      return {
        from,
        options: variables
          .filter((v) => !partial || v.key.startsWith(partial))
          .map((v) => ({
            label: v.key,
            detail: v.source === 'upstream' ? '上游输出' : '图中引用',
            apply: `\${${v.key}}`,
            type: 'variable',
          })),
        filter: true,
      };
    }

    const loneDollar = context.matchBefore(/\$$/);
    if (loneDollar) {
      return {
        from: loneDollar.from,
        options: variables.map((v) => ({
          label: v.key,
          detail: v.source === 'upstream' ? '上游输出' : '图中引用',
          apply: `\${${v.key}}`,
          type: 'variable',
        })),
        filter: false,
      };
    }

    return null;
  };
}

@Component({
  selector: 'app-spel-expression-editor',
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
  templateUrl: './spel-expression-editor.component.html',
  styleUrl: './spel-expression-editor.component.css',
})
export class SpelExpressionEditorComponent implements OnDestroy {
  @ViewChild('modalHost') modalHostRef?: ElementRef<HTMLDivElement>;

  /** 当前表达式文本 */
  value = input<string | null | undefined>(null);
  readonly = input(false);
  variables = input<SpelVariableSuggestion[]>([]);
  /** 单行输入框占位符 */
  placeholder = input('点击右侧图标编辑 SpEL');

  valueChange = output<string>();

  readonly snippets = SPEL_EXPRESSION_SNIPPETS;

  protected inlineText = signal('');
  protected modalVisible = signal(false);

  private modalEditorView?: EditorView;
  private syncingFromExternal = false;

  constructor() {
    effect(() => {
      const next = this.value();
      const str = next == null ? '' : String(next);
      this.syncingFromExternal = true;
      this.inlineText.set(str);
      this.syncingFromExternal = false;
    });
  }

  displayReadonlyValue(): string {
    const v = this.value();
    return v == null ? '' : String(v);
  }

  ngOnDestroy(): void {
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
      cmPlaceholder('Spring SpEL；输入 $ 可插入 ${变量名}（与流程分析一致）'),
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
