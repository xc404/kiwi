import { Component, computed, inject, input } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';

import { FieldEditorConfig, toFormlyConfig } from '@app/shared/components/field/field-editor';
import { FormlyModule } from '@ngx-formly/core';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { ElementModelProxyHandler } from './element-model-proxy';
import { PropertyDescription, toEditFieldConfig } from './types';
import { BpmExpressionVariableService } from '../expression/bpm-expression-variable.service';
import { ElementModel } from '../extension/element-model';

@Component({
  selector: 'property-group-edit',
  template: `
    <form nzLayout="vertical" [formGroup]="form()">
      <formly-form [fields]="fields()" [form]="form()" [model]="model()"></formly-form>
    </form>
  `,
  imports: [FormlyModule, ReactiveFormsModule, FormsModule],
  standalone: true
})
export class PropertyGroupEdit {
  elementModel = inject(ElementModel);
  private readonly expressionVariableService = inject(BpmExpressionVariableService);
  properties = input([] as PropertyDescription[]);
  bpmnModeler = input.required<BaseViewer>();
  element = input.required<Element>();
  variables = input<any[]>([]);
  currentProcessId = input<string | null | undefined>(null);
  projectId = input<string | null | undefined>(null);

  form = computed(() => {
    return new FormGroup({});
  });

  model = computed(() => {
    return new Proxy({}, new ElementModelProxyHandler(this.bpmnModeler(), this.elementModel, this.element(), this.properties(), false, this.variables()));
  });

  private spelVariableSuggestions = computed(() => {
    try {
      return this.expressionVariableService.buildSuggestions(this.bpmnModeler(), this.element());
    } catch {
      return [];
    }
  });

  fields = computed(() => {
    return this.properties().map(p => {
      const config: FieldEditorConfig = toEditFieldConfig(p);
      const baseProps: Record<string, unknown> = { variables: this.variables() };
      if (config.editor === 'expression') {
        baseProps['spelVariables'] = this.spelVariableSuggestions();
        baseProps['expressionDialect'] = this.elementModel.expressionDialect();
      }
      if (config.editor === 'process-selector') {
        baseProps['excludeProcessId'] = this.currentProcessId() ?? null;
        baseProps['projectId'] = this.projectId() ?? null;
      }
      return toFormlyConfig(config, 'vertical', baseProps);
    });
  });
}
