import { autocompletion, closeBrackets, completionKeymap, CompletionContext } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { javascript } from '@codemirror/lang-javascript';
import { EditorState } from '@codemirror/state';
import {
  EditorView,
  highlightActiveLine,
  keymap,
  lineNumbers,
  placeholder as cmPlaceholder,
} from '@codemirror/view';
import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/bpm-spel-variable-context';

interface SpelExpressionProps extends FormlyFieldProps {
  /** 来自属性面板：图中引用变量 + 上游输出 */
  spelVariables?: SpelVariableSuggestion[];
}

const SPEL_SNIPPETS: { label: string; insert: string }[] = [
  { label: '#root', insert: '#root' },
  { label: '三元', insert: '(true ? a : b)' },
  { label: 'String.format', insert: "T(java.lang.String).format('%s', #x)" },
  { label: 'Math.max', insert: 'T(java.lang.Math).max(#a, #b)' },
  { label: '非空', insert: '#x != null ? #x : \'\'' },
  { label: '字符串连接', insert: "#a + #b" },
];

function buildSpelCompletion(getVariables: () => SpelVariableSuggestion[]) {
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
  selector: 'spel-expression-editor-type',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzIconModule, NzDropDownModule, NzMenuModule],
  template: `
    <div class="spel-expression-editor">
      @if (!field.props.readonly) {
        <div class="spel-expression-editor__toolbar">
          <button nz-button nzType="dashed" nzSize="small" type="button" nz-dropdown [nzDropdownMenu]="varMenu">
            <nz-icon nzType="apartment" nzTheme="outline" />
            插入变量
          </button>
          <nz-dropdown-menu #varMenu="nzDropdownMenu">
            <ul nz-menu>
              @for (v of variables; track v.key) {
                <li nz-menu-item (click)="insertVariableRef(v)">
                  <code>{{ v.key }}</code>
                  <span class="spel-expression-editor__hint">{{ v.source === 'upstream' ? '上游' : '引用' }}</span>
                </li>
              }
              @if (variables.length === 0) {
                <li nz-menu-item nzDisabled>暂无可用变量</li>
              }
            </ul>
          </nz-dropdown-menu>

          <button nz-button nzType="dashed" nzSize="small" type="button" nz-dropdown [nzDropdownMenu]="spelMenu">
            <nz-icon nzType="function" nzTheme="outline" />
            插入 SpEL
          </button>
          <nz-dropdown-menu #spelMenu="nzDropdownMenu">
            <ul nz-menu>
              @for (s of snippets; track s.label) {
                <li nz-menu-item (click)="insertText(s.insert)">{{ s.label }}</li>
              }
            </ul>
          </nz-dropdown-menu>
        </div>
        <div #host class="spel-expression-editor__cm"></div>
      } @else {
        <div class="spel-expression-editor__readonly-fallback">{{ formControl.value ?? '' }}</div>
      }
    </div>
  `,
  styles: [
    `
      .spel-expression-editor {
        width: 100%;
      }
      .spel-expression-editor__toolbar {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 8px;
      }
      .spel-expression-editor__hint {
        margin-left: 8px;
        opacity: 0.65;
        font-size: 12px;
      }
      .spel-expression-editor__cm {
        border: 1px solid var(--ng-zorro-border-color, #d9d9d9);
        border-radius: 4px;
        min-height: 120px;
      }
      .spel-expression-editor__readonly-fallback {
        white-space: pre-wrap;
        word-break: break-word;
        font-family: monospace;
        padding: 8px;
        border: 1px solid var(--ng-zorro-border-color, #d9d9d9);
        border-radius: 4px;
        min-height: 80px;
        background: rgba(0, 0, 0, 0.02);
      }
    `,
  ],
})
export class SpelExpressionEditorType
  extends FieldType<FieldTypeConfig<SpelExpressionProps>>
  implements AfterViewInit, OnDestroy
{
  @ViewChild('host') hostRef?: ElementRef<HTMLDivElement>;

  readonly snippets = SPEL_SNIPPETS;

  private view?: EditorView;
  private syncingFromControl = false;

  get variables(): SpelVariableSuggestion[] {
    const v = this.field.props?.spelVariables;
    return Array.isArray(v) ? v : [];
  }

  ngAfterViewInit(): void {
    if (this.field.props.readonly) {
      return;
    }
    this.createEditor();
    this.formControl.valueChanges.subscribe((v) => {
      if (this.syncingFromControl || !this.view) {
        return;
      }
      const cur = this.view.state.doc.toString();
      const next = v == null ? '' : String(v);
      if (cur === next) {
        return;
      }
      this.view.dispatch({
        changes: { from: 0, to: this.view.state.doc.length, insert: next },
      });
    });
  }

  ngOnDestroy(): void {
    this.view?.destroy();
    this.view = undefined;
  }

  insertVariableRef(v: SpelVariableSuggestion): void {
    this.insertText('${' + v.key + '}');
  }

  insertText(text: string): void {
    if (!this.view || this.field.props.readonly) {
      return;
    }
    const { from } = this.view.state.selection.main;
    this.view.dispatch({
      changes: { from, insert: text },
      selection: { anchor: from + text.length },
      scrollIntoView: true,
    });
    this.view.focus();
    this.emit();
  }

  private emit(): void {
    if (!this.view) {
      return;
    }
    const text = this.view.state.doc.toString();
    this.syncingFromControl = true;
    this.formControl.setValue(text, { emitEvent: true });
    this.syncingFromControl = false;
    this.formControl.markAsDirty();
    this.formControl.markAsTouched();
  }

  private createEditor(): void {
    const host = this.hostRef?.nativeElement;
    if (!host) {
      return;
    }
    const vars = this.variables;
    const start = this.formControl.value == null ? '' : String(this.formControl.value);
    const completionSource = buildSpelCompletion(() => this.variables);

    const extensions = [
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
      EditorView.updateListener.of((u) => {
        if (u.docChanged && !this.syncingFromControl) {
          this.emit();
        }
      }),
      EditorView.theme({
        '&': { minHeight: '120px', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'monospace' },
      }),
      EditorState.allowMultipleSelections.of(false),
    ];

    this.view = new EditorView({
      state: EditorState.create({
        doc: start,
        extensions,
      }),
      parent: host,
    });
  }
}
