import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { BpmExpressionVariableService } from '@app/pages/bpm/design/expression/bpm-expression-variable.service';
import type { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/expression-variable';
import { JuelExpressionEditorComponent } from '@app/shared/components/juel-expression-editor/juel-expression-editor.component';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';

import type { CustomOutputRow } from './custom-output-row.model';

@Component({
  selector: 'bpm-custom-output-row',
  standalone: true,
  imports: [CommonModule, FormsModule, NzButtonModule, NzIconModule, NzInputModule, NzSelectModule, JuelExpressionEditorComponent],
  templateUrl: './custom-output-row.component.html',
  styleUrl: './custom-output-row.component.css'
})
export class CustomOutputRowComponent {
  private readonly expressionVariableService = inject(BpmExpressionVariableService);

  bpmnModeler = input.required<BaseViewer>();
  element = input.required<Element>();
  row = input.required<CustomOutputRow>();
  variables = input<Array<{ name?: string | null; [key: string]: unknown }>>([]);

  readonly rowChange = output<CustomOutputRow>();
  readonly remove = output<void>();

  protected readonly varNames = computed(() => {
    const raw = this.variables();
    if (!Array.isArray(raw)) {
      return [] as string[];
    }
    const names = raw.map(v => (v?.name != null ? String(v.name).trim() : '')).filter(n => n.length > 0);
    return [...new Set(names)];
  });

  protected readonly expressionVariableSuggestions = computed<SpelVariableSuggestion[]>(() => {
    let fromService: SpelVariableSuggestion[] = [];
    try {
      fromService = this.expressionVariableService.buildSuggestions(this.bpmnModeler(), this.element());
    } catch {
      fromService = [];
    }
    const byKey = new Map<string, SpelVariableSuggestion>();
    for (const v of fromService) {
      byKey.set(v.key, v);
    }
    for (const key of this.varNames()) {
      if (!byKey.has(key)) {
        byKey.set(key, { key, source: 'referenced' });
      }
    }
    return [...byKey.values()].sort((a, b) => a.key.localeCompare(b.key));
  });

  protected readonly refSelection = computed(() => {
    const text = this.row().valueText.trim();
    const m = /^\$\{([a-zA-Z0-9_]+)\}$/.exec(text);
    if (!m) {
      return null;
    }
    return this.varNames().includes(m[1]) ? m[1] : null;
  });

  protected readonly juelInlinePlaceholder = '字面量或 JUEL；$ 补全变量，右侧图标展开编辑';

  protected onNameChange(value: string): void {
    this.rowChange.emit({ ...this.row(), name: value });
  }

  protected onValueChange(value: string): void {
    this.rowChange.emit({ ...this.row(), valueText: value });
  }

  protected onRefSelect(varName: string | null): void {
    if (!varName) {
      return;
    }
    this.rowChange.emit({ ...this.row(), valueText: `\${${varName}}` });
  }

  protected requestRemove(): void {
    this.remove.emit();
  }
}
