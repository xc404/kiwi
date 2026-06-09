import { Component, computed, inject, input } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';

import { FieldEditorConfig, toFormlyConfig } from '@app/shared/components/field/field-editor';
import { FormlyModule } from '@ngx-formly/core';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { ElementModelProxyHandler } from './element-model-proxy';
import { PropertyDescription, toEditFieldConfig, toViewFieldConfig } from './types';
import { BpmExpressionVariableService } from '../expression/bpm-expression-variable.service';
import { ElementModel } from '../extension/element-model';
@Component({
  selector: 'property-group',
  template: `
    <form nzLayout="vertical" [formGroup]="form()">
      <formly-form [fields]="fields()" [form]="form()" [model]="model()"></formly-form>
    </form>
  `,
  imports: [FormlyModule, ReactiveFormsModule, FormsModule],
  standalone: true
})
export class PropertyGroup {
  elementModel = inject(ElementModel);
  private readonly expressionVariableService = inject(BpmExpressionVariableService);
  properties = input([] as PropertyDescription[]);
  bpmnModeler = input.required<BaseViewer>();
  element = input.required<Element>();
  viewMode = input(false);
  variables = input<unknown[]>([]);

  form = computed(() => {
    return new FormGroup({});
  });

  model = computed(() => {
    return new Proxy({}, new ElementModelProxyHandler(this.bpmnModeler(), this.elementModel, this.element(), this.properties(), this.viewMode(), this.variables()));
  });

  /** SpEL 编辑器：`$` 补全用的变量（上游 input/output + 组件声明 output） */
  private spelVariableSuggestions = computed(() => {
    try {
      return this.expressionVariableService.buildSuggestions(this.bpmnModeler(), this.element());
    } catch {
      return [];
    }
  });

  fields = computed(() => {
    return this.properties().map(p => {
      let config: FieldEditorConfig;
      if (this.viewMode()) {
        config = toViewFieldConfig(p);
      } else {
        config = toEditFieldConfig(p);
      }

      const baseProps: Record<string, unknown> = { variables: this.variables() };
      if (config.editor === 'expression') {
        baseProps['spelVariables'] = this.spelVariableSuggestions();
        baseProps['expressionDialect'] = this.elementModel.expressionDialect();
      }
      return toFormlyConfig(config, 'vertical', baseProps);
    });
  });
}
