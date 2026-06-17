import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { isTextType, PropertyDescription } from './types';
import { ElementModel } from '../extension/element-model';

export class ElementModelProxyHandler {
  constructor(
    private bpmnModeler: BaseViewer,
    private elementModel: ElementModel,
    private element: Element,
    private properties: PropertyDescription[],
    private viewMode = false,
    private variables: any[] = []
  ) {}

  get(target: any, prop: string, _receiver: any) {
    const property = this.properties.find(p => p.key == prop);
    if (property) {
      const value = this.elementModel.getValue(this.bpmnModeler, this.element, property.namespace ?? 'bpmn', property.key);
      if (this.viewMode && isTextType(property)) {
        const rawValue = this.variables.find(v => v.name === prop)?.value;
        if (!value && !rawValue) {
          return undefined;
        }
        if (rawValue !== undefined && rawValue != value) {
          return `${rawValue} (${value})`;
        }
      }
      return value;
    }
  }

  set(target: any, prop: string, value: any, _receiver: any) {
    if (this.viewMode) {
      return true;
    }
    const property = this.properties.find(p => p.key == prop);
    if (property) {
      this.elementModel.setValue(this.bpmnModeler, this.element, property.namespace ?? 'bpmn', property.key, value);
      return true;
    }
    return false;
  }
}
