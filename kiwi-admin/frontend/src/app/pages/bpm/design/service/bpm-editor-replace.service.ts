import { inject, Injectable } from '@angular/core';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import {
  ComponentDescription,
} from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';
import { ElementModel } from '../extension/element-model';
import { PropertyNamespace } from '../property-panel/types';

function catalogOutputKeys(component: ComponentDescription | undefined): Set<string> {
  return new Set((component?.outputParameters ?? []).map((p) => p.key));
}

/** 上下文菜单：原地替换 ServiceTask 业务组件并合并参数 */
@Injectable()
export class BpmEditorReplaceService {
  private readonly componentService = inject(ComponentService);
  private readonly elementModel = inject(ElementModel);

  private modeler!: BpmnModeler;

  init(modeler: BpmnModeler): void {
    this.modeler = modeler;
  }

  replaceComponentFromContextPad(element: Element, newComponent: ComponentDescription): void {
    const oldComponent = this.componentService.getComponentForElement(element);
    if (!oldComponent) {
      return;
    }
    if ((element as { type?: string }).type !== 'bpmn:ServiceTask') {
      return;
    }

    const modeler = this.modeler;
    const oldCatalogOut = catalogOutputKeys(oldComponent);

    const preservedInputs = new Map<string, string>();
    for (const p of newComponent.inputParameters ?? []) {
      if (p.hidden) {
        console.log('hidden input parameter', p.key, p.defaultValue);
        continue;
      }
      const v = this.elementModel.getValue(modeler, element, PropertyNamespace.inputParameter, p.key);
      if (v != null && v !== '') {
        preservedInputs.set(p.key, String(v));
      }
    }

    const customOutputs = new Map<string, string>();
    for (const p of this.elementModel.getOutputParameters(element)) {
      const name = String((p as any).get('name'));
      if (!oldCatalogOut.has(name)) {
        const v = this.elementModel.getValue(modeler, element, PropertyNamespace.outputParameter, name);
        customOutputs.set(name, v == null ? '' : String(v));
      }
    }

    for (const p of [...this.elementModel.getInputParameters(element)]) {
      this.elementModel.removeInputParameter(modeler, element, String((p as any).get('name')));
    }
    for (const p of [...this.elementModel.getOutputParameters(element)]) {
      this.elementModel.removeOutputParameter(modeler, element, String((p as any).get('name')));
    }

    this.componentService.setComponentId(modeler, element, newComponent);

    const inputParams = newComponent.inputParameters ?? [];
    for (const p of inputParams) {
      this.elementModel.setValue(
        modeler,
        element,
        PropertyNamespace.inputParameter,
        p.key,
        p.defaultValue ?? '',
      );
    }
    for (const p of inputParams) {
      if (!preservedInputs.has(p.key)) {
        continue;
      }
      this.elementModel.setValue(
        modeler,
        element,
        PropertyNamespace.inputParameter,
        p.key,
        preservedInputs.get(p.key)!,
      );
    }

    for (const [name, valueText] of customOutputs.entries()) {
      this.elementModel.setValue(
        modeler,
        element,
        PropertyNamespace.outputParameter,
        name,
        valueText,
      );
    }
  }
}
