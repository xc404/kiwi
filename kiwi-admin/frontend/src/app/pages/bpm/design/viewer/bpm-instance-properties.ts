import { Component, input } from '@angular/core';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzTableModule } from 'ng-zorro-antd/table';
import { ProcessVariableRow } from '../service/process-instance.service';

@Component({
  selector: 'bpm-instance-properties',
  templateUrl: './bpm-instance-properties.html',
  styleUrl: './bpm-instance-properties.scss',
  imports: [NzCollapseModule, NzTableModule],
  standalone: true,
})
export class BpmInstanceProperties {
  /** 按当前选中图元过滤后的变量行 */
  readonly variables = input.required<ProcessVariableRow[]>();
  /** 是否选中流程根（流程级变量） */
  readonly selectionIsRoot = input.required<boolean>();

  protected formatVariableValue(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch {
        return String(value);
      }
    }
    return String(value);
  }

  protected scopeLabel(scope: 'process' | 'activity'): string {
    return scope === 'process' ? '流程' : '节点';
  }
}
