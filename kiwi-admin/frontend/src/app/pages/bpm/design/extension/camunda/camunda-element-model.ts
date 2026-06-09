// import { BpmnModeler } from 'bpmn-js/lib/Modeler';
import { inject } from '@angular/core';

import { ProcessDesignService } from '@app/pages/bpm/design/service/process-design.service';
import { ComponentDescription, ComponentProvider } from '@app/pages/bpm/flow-elements/component-provider';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { Element } from 'bpmn-js/lib/model/Types';
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json';

import { ElementModel } from '../element-model';
import { decodeCamundaInputLiteral, encodeCamundaInputLiteral } from './camunda-input-literal';

/** 组件赋值为 JSON 字符串，需按字面量持久化，避免 Camunda 将 body 当 JUEL 解析 */
const INPUT_PARAMETER_LITERAL_STRING_KEYS = new Set<string>(['assignments']);
export class CamundaElementModel extends ElementModel {
  override expressionDialect(): 'juel' {
    return 'juel';
  }

  componentProvider = inject(ComponentProvider);
  private readonly processDesignService = inject(ProcessDesignService);

  override getModdleExtension() {
    return camundaModdleDescriptor;
  }

  public override getValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): any {
    if (element.type === 'bpmn:SequenceFlow' && key === 'condition') {
      return element.businessObject.conditionExpression?.body ?? null;
    }
    if (element.type === 'bpmn:CallActivity' && namespace === 'In') {
      const binding = this.getCallActivityIns(element).find((i: any) => i.get('target') === key);
      if (binding) {
        const src = binding.get('source');
        if (src != null && src !== '') {
          return src;
        }
        return binding.get('sourceExpression') ?? null;
      }
      return null;
    }
    if (element.type === 'bpmn:CallActivity' && namespace === 'Out') {
      const binding = this.getCallActivityOuts(element).find((o: any) => o.get('source') === key);
      if (binding) {
        return binding.get('target') ?? null;
      }
      return null;
    }
    if (namespace == 'inputParameter') {
      const ele: any = this.getInputParameter(element, key);
      if (ele) {
        const raw = ele.get('value');
        if (INPUT_PARAMETER_LITERAL_STRING_KEYS.has(key) && typeof raw === 'string') {
          return decodeCamundaInputLiteral(raw);
        }
        return raw;
      }
      return null;
    }
    if (namespace == 'outputParameter') {
      const ele: any = this.getOutputParameter(element, key);
      if (ele) {
        return ele.get('value');
      }
      return null;
    }

    if ((namespace === 'element' || !namespace) && (key === 'componentId' || key === 'processId')) {
      const fromProperty = this.readExtensionPropertyValue(element, key);
      if (fromProperty != null && fromProperty !== '') {
        return fromProperty;
      }
      return element.businessObject[key] ?? null;
    }

    return super.getValue(bpmnModeler, element, namespace, key);
  }

  public override setValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string, value: any): void {
    if (element.type === 'bpmn:SequenceFlow' && key === 'condition') {
      const str = value == null ? '' : String(value);
      if (!str) {
        this.updateProperties(bpmnModeler, element, {
          conditionExpression: undefined
        });
        return;
      }
      const conditionExpression = this.createElement(bpmnModeler, 'bpmn:FormalExpression', {
        body: str
      });
      this.updateProperties(bpmnModeler, element, {
        conditionExpression,
        name: str.trim()
      });
      return;
    }
    if (element.type === 'bpmn:CallActivity' && namespace === 'In') {
      const inEl = this.getOrCreateCallActivityIn(bpmnModeler, element, key);
      const str = value == null ? '' : String(value);
      this.updateModdleProperties(bpmnModeler, element, inEl, {
        source: str,
        sourceExpression: undefined
      });
      return;
    }
    if (element.type === 'bpmn:CallActivity' && namespace === 'Out') {
      const outEl = this.getOrCreateCallActivityOut(bpmnModeler, element, key);
      const str = value == null ? '' : String(value);
      this.updateModdleProperties(bpmnModeler, element, outEl, {
        target: str
      });
      return;
    }
    if (namespace == 'inputParameter' || namespace == 'outputParameter') {
      const parameter: Element = this.getOrCreateInputOutputParameter(bpmnModeler, element, namespace, key);
      let stored = value;
      if (namespace === 'inputParameter' && INPUT_PARAMETER_LITERAL_STRING_KEYS.has(key) && value != null) {
        stored = encodeCamundaInputLiteral(String(value));
      }
      this.updateModdleProperties(bpmnModeler, element, parameter, {
        value: stored
      });
      return;
    }
    if (namespace == 'property') {
      const parameter: Element = this.getOrCreatePropertyParameter(bpmnModeler, element, key);
      this.updateModdleProperties(bpmnModeler, element, parameter, {
        value: value
      });
      return;
    }

    if (element.type === 'bpmn:CallActivity' && namespace === 'element') {
      if (key === 'componentId') {
        this.setComponentId(bpmnModeler, element, value);
        return;
      }
      if (key === 'processId') {
        this.setProcessId(bpmnModeler, element, value == null ? '' : String(value));
        return;
      }
    }

    if (element.type == 'bpmn:ServiceTask') {
      if (namespace == 'element') {
        if (key == 'componentId') {
          this.setComponentId(bpmnModeler, element, value);
        }
        return;
      }
    }
    super.setValue(bpmnModeler, element, namespace, key, value);
  }

  setComponentId(bpmnModeler: BpmnModeler, element: Element, componentId: string) {
    super.setValue(bpmnModeler, element, 'element', 'componentId', componentId);
    const component: ComponentDescription = this.componentProvider.getComponent(componentId) as any;
    if (!component) {
      return;
    }
    const id = component.id;
    const componentKey = component.key;
    this.setValue(bpmnModeler, element, 'property', 'componentId', id);
    if (component.type == 'SpringBean') {
      this.updateProperties(bpmnModeler, element, {
        'camunda:delegateExpression': `\${${componentKey}}`
      });
    }

    if (component.type == 'SpringExternalTask') {
      this.updateProperties(bpmnModeler, element, {
        'camunda:type': 'external',
        'camunda:topic': componentKey
      });
    }

    if (component.type === 'CallActivity' && element.type === 'bpmn:CallActivity') {
      this.removeExtensionProperty(bpmnModeler, element, 'processId');
      const calledElement = this.resolveCalledElementFromComponent(component);
      this.updateProperties(bpmnModeler, element, {
        calledElement,
        name: this.formatCallActivityName(component.name)
      });
      this.ensurePropagateAllVariables(bpmnModeler, element);
    }
  }

  setProcessId(bpmnModeler: BpmnModeler, element: Element, processId: string) {
    const trimmed = processId.trim();
    if (!trimmed) {
      this.removeExtensionProperty(bpmnModeler, element, 'processId');
      this.updateProperties(bpmnModeler, element, { calledElement: undefined });
      return;
    }
    this.removeExtensionProperty(bpmnModeler, element, 'componentId');
    super.setValue(bpmnModeler, element, 'element', 'processId', trimmed);
    this.setValue(bpmnModeler, element, 'property', 'processId', trimmed);
    this.updateProperties(bpmnModeler, element, { calledElement: trimmed });
    this.ensurePropagateAllVariables(bpmnModeler, element);
    this.applyCallActivityNameFromProcessId(bpmnModeler, element, trimmed);
  }

  private formatCallActivityName(processName: string): string {
    const trimmed = processName.trim();
    return trimmed ? `调用<${trimmed}>` : '';
  }

  private applyCallActivityNameFromProcessId(bpmnModeler: BpmnModeler, element: Element, processId: string): void {
    const cached = this.processDesignService.resolveProcessDisplayName(processId);
    if (cached) {
      this.updateProperties(bpmnModeler, element, {
        name: this.formatCallActivityName(cached)
      });
      return;
    }
    this.processDesignService.getProcessById(processId).subscribe({
      next: p => {
        const display = (p.name ?? '').trim() || processId;
        this.processDesignService.cacheProcessDisplayNames([p]);
        this.updateProperties(bpmnModeler, element, {
          name: this.formatCallActivityName(display)
        });
      }
    });
  }

  /** Camunda「Propagate all variables」：camunda:in/out variables="all" */
  override ensurePropagateAllVariables(bpmnModeler: BpmnModeler, element: Element): void {
    if (element.type !== 'bpmn:CallActivity') {
      return;
    }
    const ins = this.getCallActivityIns(element);
    if (!ins.some((i: { get: (k: string) => unknown }) => i.get('variables') === 'all')) {
      const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
      const inEl = this.createElement(bpmnModeler, 'camunda:In', { variables: 'all' }, extensionElements);
      this.updateModdleProperties(bpmnModeler, element, extensionElements, {
        values: [...extensionElements.get('values'), inEl]
      });
    }
    const outs = this.getCallActivityOuts(element);
    if (!outs.some((o: { get: (k: string) => unknown }) => o.get('variables') === 'all')) {
      const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
      const outEl = this.createElement(bpmnModeler, 'camunda:Out', { variables: 'all' }, extensionElements);
      this.updateModdleProperties(bpmnModeler, element, extensionElements, {
        values: [...extensionElements.get('values'), outEl]
      });
    }
  }

  private resolveCalledElementFromComponent(component: ComponentDescription): string {
    const key = component.key ?? '';
    if (key.startsWith('process:')) {
      return key.slice('process:'.length);
    }
    return component.id;
  }

  private readExtensionPropertyValue(element: Element, key: string): string | null {
    const properties = this.getExtensionProperties(element);
    if (!properties) {
      return null;
    }
    const values: Array<{ get: (k: string) => unknown }> = properties['get']('values') || [];
    const match = values.find(p => p.get('name') === key);
    if (!match) {
      return null;
    }
    const v = match.get('value');
    return v == null ? null : String(v);
  }

  private removeExtensionProperty(bpmnModeler: BpmnModeler, element: Element, key: string): void {
    const properties = this.getExtensionProperties(element);
    if (!properties) {
      return;
    }
    const values: Array<{ get: (k: string) => unknown }> = [...(properties['get']('values') || [])];
    const next = values.filter(p => p.get('name') !== key);
    if (next.length === values.length) {
      return;
    }
    this.updateModdleProperties(bpmnModeler, element, properties, { values: next });
  }

  getOrCreateInputOutputParameter(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): Element {
    // (2) ensure inputOutput
    const inputOutput = this.ensureInputOutputElements(bpmnModeler, element);
    let parameter: Element | null = null as any;
    if (namespace == 'inputParameter') {
      parameter = this.getInputParameter(element, key);
      if (!parameter) {
        const parent = inputOutput;
        parameter = this.createElement(
          bpmnModeler,
          'camunda:InputParameter',
          {
            name: key
          },
          parent
        );
        this.updateModdleProperties(bpmnModeler, element, inputOutput, {
          inputParameters: [...inputOutput.get('inputParameters'), parameter]
        });
      }
    }
    if (namespace == 'outputParameter') {
      parameter = this.getOutputParameter(element, key);
      if (!parameter) {
        const parent = inputOutput;
        parameter = this.createElement(
          bpmnModeler,
          'camunda:OutputParameter',
          {
            name: key
          },
          parent
        );
        this.updateModdleProperties(bpmnModeler, element, inputOutput, {
          outputParameters: [...inputOutput.get('outputParameters'), parameter]
        });
      }
    }
    return parameter!;
  }

  getOrCreatePropertyParameter(bpmnModeler: BpmnModeler, element: Element, key: string): Element {
    // (2) ensure extensionProperties
    const extensionProperties: any = this.ensureExtensionPropertiesElements(bpmnModeler, element);
    const values: any[] = extensionProperties.get('values') || [];
    const matching = values.filter((p: any) => p.get('name') === key);
    let parameter = matching[0] as Element | undefined;
    if (matching.length > 1) {
      const deduped = values.filter((p: any) => p.get('name') !== key || p === matching[0]);
      this.updateModdleProperties(bpmnModeler, element, extensionProperties, {
        values: deduped
      });
    }
    if (!parameter) {
      const parent = extensionProperties;
      parameter = this.createElement(
        bpmnModeler,
        'camunda:Property',
        {
          name: key
        },
        parent
      );
      this.updateModdleProperties(bpmnModeler, element, parent, {
        values: [...extensionProperties.get('values'), parameter]
      });
    }
    return parameter!;
  }

  private ensureExtensionElements(bpmnModeler: BpmnModeler<null>, root: Element) {
    const businessObject = ModelUtil.getBusinessObject(root);
    let extensionElements = businessObject.get('extensionElements');
    if (!extensionElements) {
      extensionElements = this.createElement(
        bpmnModeler,
        'bpmn:ExtensionElements',
        {
          values: []
        },
        businessObject
      );
      this.updateModdleProperties(bpmnModeler, root, businessObject, {
        extensionElements
      });
    }
    return extensionElements;
  }

  private ensureInputOutputElements(bpmnModeler: BpmnModeler<null>, root: Element) {
    const extensionElements = this.ensureExtensionElements(bpmnModeler, root);
    let inputOutput = this.getInputOutput(root);
    if (!inputOutput) {
      const parent = extensionElements;
      inputOutput = this.createElement(
        bpmnModeler,
        'camunda:InputOutput',
        {
          inputParameters: [],
          outputParameters: []
        },
        parent
      );
      this.updateModdleProperties(bpmnModeler, root, extensionElements, {
        values: [...extensionElements.get('values'), inputOutput]
      });
    }
    return inputOutput;
  }

  private getInputParameter(root: Element, key: string): Element | null {
    const inputs: any[] = this.getInputParameters(root);

    const e = inputs.filter(i => i.get('name') === key);
    if (e.length > 0) {
      return e[0];
    }
    return null;
  }

  private getOutputParameter(root: Element, key: string): Element | null {
    const output: any[] = this.getOutputParameters(root);
    const e = output.filter(i => i.get('name') === key);
    if (e.length > 0) {
      return e[0];
    }
    return null;
  }

  private getCallActivityIns(element: Element): any[] {
    return this.getExtensionElementsList(element, 'camunda:In');
  }

  private getCallActivityOuts(element: Element): any[] {
    return this.getExtensionElementsList(element, 'camunda:Out');
  }

  private getOrCreateCallActivityIn(bpmnModeler: BpmnModeler, element: Element, targetVar: string): Element {
    const existing = this.getCallActivityIns(element).find((i: any) => i.get('target') === targetVar);
    if (existing) {
      return existing;
    }
    const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
    const inEl = this.createElement(
      bpmnModeler,
      'camunda:In',
      {
        target: targetVar,
        source: ''
      },
      extensionElements
    );
    this.updateModdleProperties(bpmnModeler, element, extensionElements, {
      values: [...extensionElements.get('values'), inEl]
    });
    return inEl;
  }

  private getOrCreateCallActivityOut(bpmnModeler: BpmnModeler, element: Element, sourceVar: string): Element {
    const existing = this.getCallActivityOuts(element).find((o: any) => o.get('source') === sourceVar);
    if (existing) {
      return existing;
    }
    const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
    const outEl = this.createElement(
      bpmnModeler,
      'camunda:Out',
      {
        source: sourceVar,
        target: ''
      },
      extensionElements
    );
    this.updateModdleProperties(bpmnModeler, element, extensionElements, {
      values: [...extensionElements.get('values'), outEl]
    });
    return outEl;
  }

  private getInputOutput(root: Element) {
    // if (ModelUtil.is(element, 'camunda:Connector')) {
    //     return element.businessObject.get('inputOutput');
    // }
    const businessObject = ModelUtil.getBusinessObject(root);
    return (this.getElements(businessObject, 'camunda:InputOutput') || [])[0];
  }

  override getInputParameters(element: Element): Element[] {
    const inputOutput = this.getInputOutput(element);
    return (inputOutput && inputOutput.get('inputParameters')) || [];
  }

  override getOutputParameters(element: Element): Element[] {
    // return this.getParameters(element, 'outputParameters');
    const inputOutput = this.getInputOutput(element);
    return (inputOutput && inputOutput.get('outputParameters')) || [];
  }

  override getCallActivityInTargets(element: Element): string[] {
    if (element.type !== 'bpmn:CallActivity') {
      return [];
    }
    return this.getCallActivityIns(element)
      .map((i: { get: (k: string) => unknown }) => String(i.get('target') ?? '').trim())
      .filter(k => k.length > 0);
  }

  override getCallActivityOutSources(element: Element): string[] {
    if (element.type !== 'bpmn:CallActivity') {
      return [];
    }
    return this.getCallActivityOuts(element)
      .map((o: { get: (k: string) => unknown }) => String(o.get('source') ?? '').trim())
      .filter(k => k.length > 0);
  }

  override removeInputParameter(bpmnModeler: BaseViewer, element: Element, key: string): void {
    const inputOutput = this.ensureInputOutputElements(bpmnModeler as BpmnModeler, element);
    const existing: any[] = [...(inputOutput.get('inputParameters') || [])];
    const next = existing.filter((p: any) => String(p.get('name')) !== key);
    if (next.length === existing.length) {
      return;
    }
    this.updateModdleProperties(bpmnModeler, element, inputOutput, {
      inputParameters: next
    });
  }

  override removeOutputParameter(bpmnModeler: BaseViewer, element: Element, key: string): void {
    const inputOutput = this.ensureInputOutputElements(bpmnModeler as BpmnModeler, element);
    const existing: any[] = [...(inputOutput.get('outputParameters') || [])];
    const next = existing.filter((p: any) => String(p.get('name')) !== key);
    if (next.length === existing.length) {
      return;
    }
    this.updateModdleProperties(bpmnModeler, element, inputOutput, {
      outputParameters: next
    });
  }

  private ensureExtensionPropertiesElements(bpmnModeler: BpmnModeler<null>, root: Element) {
    const extensionElements = this.ensureExtensionElements(bpmnModeler, root);
    let inputOutput = this.getExtensionProperties(root);
    if (!inputOutput) {
      const parent = extensionElements;
      inputOutput = this.createElement(
        bpmnModeler,
        'camunda:Properties',
        {
          values: []
        },
        parent
      );
      this.updateModdleProperties(bpmnModeler, root, extensionElements, {
        values: [...extensionElements.get('values'), inputOutput]
      });
    }
    return inputOutput;
  }

  getExtensionProperties(root: Element): Element {
    const businessObject = ModelUtil.getBusinessObject(root);
    return (this.getElements(businessObject, 'camunda:Properties') || [])[0];
  }

  getExtensionProperty(root: Element, key: string): Element | null {
    const propertyEles: any = this.getExtensionProperties(root);

    // camunda:Properties stores child camunda:property elements in `values`, not `property`
    const properties: any[] = (propertyEles && propertyEles.get('values')) || [];

    const e = properties.filter(i => i.get('name') === key);
    if (e.length > 0) {
      return e[0];
    }
    return null;
  }

  getElements(root: Element, type?: string, property?: string) {
    const businessObject = ModelUtil.getBusinessObject(root);
    const elements = this.getExtensionElementsList(businessObject, type);
    return !property ? elements : (elements[0] || {})[property] || [];
  }

  getExtensionElementsList(root: Element, type?: string): Element[] {
    const businessObject = ModelUtil.getBusinessObject(root);
    const extensionElements = businessObject.get('extensionElements');
    if (!extensionElements) {
      return [];
    }
    const values = extensionElements.get('values');
    if (!values || !values.length) {
      return [];
    }
    if (type) {
      return values.filter((value: Element) => ModelUtil.is(value, type));
    }
    return values;
  }
}
