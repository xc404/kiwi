import {
  AfterViewInit,
  Component,
  ElementRef,
  input,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import { defaultKeymap, historyKeymap } from '@codemirror/commands';
import { json, jsonParseLinter } from '@codemirror/lang-json';
import { linter, lintGutter } from '@codemirror/lint';
import { EditorState, Extension } from '@codemirror/state';
import {
  EditorView,
  highlightActiveLine,
  keymap,
  lineNumbers,
} from '@codemirror/view';

/**
 * 基于 CodeMirror 6 的 JSON 文本编辑：语法高亮、括号匹配、JSON.parse 实时校验（与 SpEL 编辑器同栈）。
 */
@Component({
  selector: 'app-json-code-editor',
  standalone: true,
  template: '<div #host class="app-json-code-editor-host" role="textbox" aria-label="JSON 编辑"></div>',
  styleUrl: './json-code-editor.component.css',
})
export class JsonCodeEditorComponent implements AfterViewInit, OnDestroy {
  @ViewChild('host') hostRef!: ElementRef<HTMLDivElement>;

  /** 挂载时的初始文本（弹窗打开前由父组件设好） */
  initialText = input<string>('{}');

  private view?: EditorView;

  ngAfterViewInit(): void {
    this.mountEditor();
  }

  ngOnDestroy(): void {
    this.view?.destroy();
    this.view = undefined;
  }

  /** 当前文档全文（用于提交、校验） */
  getDocumentText(): string {
    return this.view?.state.doc.toString() ?? '';
  }

  private mountEditor(): void {
    const host = this.hostRef?.nativeElement;
    if (!host) {
      return;
    }
    this.view?.destroy();

    const extensions: Extension[] = [
      lineNumbers(),
      highlightActiveLine(),
      lintGutter(),
      linter(jsonParseLinter()),
      json(),
      keymap.of([...defaultKeymap, ...historyKeymap]),
      EditorView.lineWrapping,
      EditorView.theme({
        '&': { minHeight: '260px', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' },
        '.cm-content': { paddingBlock: '8px' },
      }),
      EditorState.allowMultipleSelections.of(false),
    ];

    this.view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: this.initialText(),
        extensions,
      }),
    });
  }
}
