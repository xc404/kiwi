import { Component, computed, inject, input } from '@angular/core';

import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { ElementModel } from '../extension/element-model';
import type { BpmnRuntimeVariable } from './readonly-property-row/bpmn-runtime-variable.model';
import { ReadonlyPropertyRowComponent } from './readonly-property-row/readonly-property-row.component';
import { PropertyDescription } from './types';

function modelValueToConfiguredText(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
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

@Component({
  selector: 'property-group-readonly',
  template: `
    @for (p of enrichedProperties(); track p.key) {
      <bpm-readonly-property-row [propertyDescription]="p" [runtimeWarningEnabled]="runtimeWarningEnabled()" [variables]="variables()" />
    }
  `,
  imports: [ReadonlyPropertyRowComponent],
  standalone: true
})
export class PropertyGroupReadonly {
  elementModel = inject(ElementModel);
  properties = input([] as PropertyDescription[]);
  bpmnModeler = input.required<BaseViewer>();
  element = input.required<Element>();
  variables = input<BpmnRuntimeVariable[]>([]);
  /** 仅在输入/输出 Tab 的运行时查看场景下开启「运行时值缺失」告警 */
  runtimeWarningEnabled = input<boolean>(false);

  /** 从元素模型填充 `valueText`，只读行再与 `variables` 合并展示（与原 Proxy + Formly 行为对齐） */
  enrichedProperties = computed(() => {
    const modeler = this.bpmnModeler();
    const element = this.element();
    return this.properties().map(p => {
      const fromModel = this.elementModel.getValue(modeler, element, p.namespace ?? 'bpmn', p.key);
      const text = modelValueToConfiguredText(fromModel);
      if (text !== undefined) {
        return { ...p, valueText: text };
      }
      return { ...p };
    });
  });
}
