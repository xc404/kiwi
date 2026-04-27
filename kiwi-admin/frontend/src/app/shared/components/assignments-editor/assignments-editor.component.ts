import { CommonModule } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/bpm-spel-variable-context';
import { JuelExpressionEditorComponent } from '@app/shared/components/juel-expression-editor/juel-expression-editor.component';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import {
  AssignmentRow,
  parseAssignments,
  parseValueText,
} from './assignments-editor.utils';

@Component({
  selector: 'app-assignments-editor',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NzButtonModule,
    NzIconModule,
    NzInputModule,
    NzSelectModule,
    NzTooltipModule,
    JuelExpressionEditorComponent,
  ],
  templateUrl: './assignments-editor.component.html',
  styleUrl: './assignments-editor.component.css',
})
export class AssignmentsEditorComponent {
  /** 绑定值：JSON 字符串或已解析数组 */
  value = input<unknown>(undefined);
  readonly = input(false);
  /** 运行时变量名列表（属性面板传入） */
  variables = input<Array<{ name?: string | null; [key: string]: unknown }>>([]);

  valueChange = output<string>();

  protected rows = signal<AssignmentRow[]>([{ key: '', valueText: '' }]);

  private suppressSync = false;

  protected varNames = computed(() => {
    const raw = this.variables();
    if (!Array.isArray(raw)) {
      return [] as string[];
    }
    const names = raw
      .map((v) => (v?.name != null ? String(v.name).trim() : ''))
      .filter((n) => n.length > 0);
    return [...new Set(names)];
  });

  /** JUEL 编辑器变量补全（与属性面板运行时变量一致） */
  protected expressionVariableSuggestions = computed<SpelVariableSuggestion[]>(() =>
    this.varNames().map((key) => ({ key, source: 'referenced' as const })),
  );

  /** 单行输入占位（完整编辑见右侧图标弹窗） */
  readonly juelInlinePlaceholder =
    '字面量或 JUEL；$ 补全变量，右侧图标展开编辑';

  protected refSelection = computed(() => {
    const names = this.varNames();
    return this.rows().map((r) => {
      const t = r.valueText.trim();
      const m = /^\$\{([a-zA-Z0-9_]+)\}$/.exec(t);
      if (m && names.includes(m[1])) {
        return m[1];
      }
      return null;
    });
  });

  protected payloadRows = computed(() =>
    this.rows()
      .filter((r) => r.key.trim() !== '')
      .map((r) => ({ key: r.key.trim(), value: parseValueText(r.valueText) })),
  );

  constructor() {
    effect(() => {
      const v = this.value();
      if (this.suppressSync) {
        this.suppressSync = false;
        return;
      }
      this.rows.set(parseAssignments(v));
    });
  }

  formatDisplayValue(value: unknown): string {
    if (value === undefined) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    return JSON.stringify(value);
  }

  onKeyChange(index: number, value: string): void {
    this.rows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, key: value } : r)),
    );
    this.emit();
  }

  onValueChange(index: number, value: string): void {
    this.rows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, valueText: value } : r)),
    );
    this.emit();
  }

  onRefSelect(index: number, varName: string | null): void {
    if (varName == null || varName === '') {
      return;
    }
    this.rows.update((rows) =>
      rows.map((r, i) =>
        i === index ? { ...r, valueText: '${' + varName + '}' } : r,
      ),
    );
    this.emit();
  }

  addRow(): void {
    this.rows.update((rows) => [...rows, { key: '', valueText: '' }]);
    this.emit();
  }

  removeRow(index: number): void {
    this.rows.update((rows) => {
      if (rows.length <= 1) {
        return [{ key: '', valueText: '' }];
      }
      return rows.filter((_, i) => i !== index);
    });
    this.emit();
  }

  duplicateRow(index: number): void {
    this.rows.update((rows) => {
      const r = rows[index];
      return [...rows.slice(0, index + 1), { key: r.key, valueText: r.valueText }, ...rows.slice(index + 1)];
    });
    this.emit();
  }

  private emit(): void {
    const payload = this.payloadRows();
    const json = JSON.stringify(payload);
    this.suppressSync = true;
    this.valueChange.emit(json);
  }
}
