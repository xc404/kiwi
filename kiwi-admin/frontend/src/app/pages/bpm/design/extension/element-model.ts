import { ExpressionDialect } from '@app/shared/formly/types/expression-editor-type';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import Modeling from 'bpmn-js/lib/features/modeling/Modeling';
import { Element } from 'bpmn-js/lib/model/Types';
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
export abstract class ElementModel {
  abstract getModdleExtension(): any;

  /**
   * 属性面板统一 expression 编辑器默认方言。
   */
  expressionDialect(): ExpressionDialect {
    return 'spel';
  }

  /**
   * 兼容旧逻辑：统一返回 expression type。
   */
  expressionEditorFormlyType(): string {
    return 'expression';
  }

  public getValue(bpmnModeler: BaseViewer, element: Element, namespace: string, key: string): any {
    if (namespace == 'bpmn' || namespace == 'element' || !namespace) {
      switch (key) {
        case 'name':
        case 'id':
        case 'componentId':
          return element.businessObject[key];
          break;
      }
    }

    if (element.type == 'bpmn:sequenceFlow') {
      if (key == 'condition') {
        return element.businessObject.conditionExpression?.body;
      }
    }

    // 事件 / 接收任务专属字段：messageName / signalName / timerType / timerValue
    const eventValue = this.getEventValue(element, key);
    if (eventValue !== undefined) {
      return eventValue;
    }
  }

  public setValue(bpmnModeler: BaseViewer, element: Element, namespace: string, key: string, value: any): void {
    if (namespace == 'bpmn' || namespace == 'element' || !namespace) {
      switch (key) {
        case 'name':
        case 'id':
        case 'componentId':
          this.updateProperties(bpmnModeler, element, {
            [key]: value
          });
          return;
      }
    }

    if (this.setEventValue(bpmnModeler, element, key, value)) {
      return;
    }
  }

  public clearComponentProperties(bpmnModeler: BaseViewer, element: Element) {
    if (element.type == 'bpmn:ServiceTask') {
      const businessObject = ModelUtil.getBusinessObject(element);
      this.updateModdleProperties(bpmnModeler, element, businessObject, {
        extensionElements: null
      });
    }
  }

  public getChildElement(parent: Element, ...children: string[]): Element {
    let ele = parent;
    for (const child of children) {
      ele = ele.businessObject[child];
      if (ele == null) break;
    }
    return ele;
  }

  public getOrCreateChildElement(bpmnModeler: BpmnModeler, parent: Element, ...children: string[]): Element {
    const bpmnFactory: BpmnFactory = bpmnModeler.get('bpmnFactory');
    let ele = parent;
    for (const child of children) {
      let childEle = ele.businessObject[child];
      if (childEle == null) {
        childEle = bpmnFactory.create(child);
        ele.businessObject[child] = childEle;
      }
      ele = childEle;
    }
    return ele;
  }

  updateProperties(bpmnModeler: BaseViewer, element: Element, properties: any) {
    const bpmnFactory: BpmnFactory = bpmnModeler.get('bpmnFactory');
    const modeling: Modeling = bpmnModeler.get('modeling');
    modeling.updateProperties(element, properties);
  }

  updateModdleProperties(bpmnModeler: BaseViewer, element: Element, moddleElement: any, properties: any) {
    const modeling: any = bpmnModeler.get('modeling');
    modeling.updateModdleProperties(element, moddleElement, properties);
  }

  createElement(bpmnModeler: BaseViewer, type: string, properties: any, parent?: Element) {
    const bpmnFactory: any = bpmnModeler.get('bpmnFactory');
    const element = bpmnFactory.create(type, properties);
    if (parent) {
      element.$parent = parent;
    }
    return element;
  }

  /** Camunda/Flowable 子类返回 extensionElements 下 inputOutput 的 inputParameters；默认无 */
  getInputParameters(_element: Element): Element[] {
    return [];
  }

  /** Camunda/Flowable 子类返回 extensionElements 下 inputOutput 的 outputParameters；默认无 */
  getOutputParameters(_element: Element): Element[] {
    return [];
  }

  /** 删除单个 inputParameter（按 name）；默认无实现 */
  removeInputParameter(_bpmnModeler: BaseViewer, _element: Element, _key: string): void {}

  /** 删除单个 outputParameter（按 name）；默认无实现 */
  removeOutputParameter(_bpmnModeler: BaseViewer, _element: Element, _key: string): void {}

  /** CallActivity：extension 下 camunda:In 的 target（子流程入参变量名） */
  getCallActivityInTargets(_element: Element): string[] {
    return [];
  }

  /** CallActivity：extension 下 camunda:Out 的 source（父流程变量名） */
  getCallActivityOutSources(_element: Element): string[] {
    return [];
  }

  /** CallActivity：默认开启 propagate all variables（Camunda 子类实现） */
  ensurePropagateAllVariables(_bpmnModeler: BaseViewer, _element: Element): void {}

  /* ========================================================================
   * 事件 / 接收任务专属字段：messageName / signalName / timerType / timerValue
   *
   * 处理思路：
   *  - messageName：
   *    · ReceiveTask 用 businessObject.messageRef 顶层属性
   *    · IntermediateCatchEvent + MessageEventDefinition 用 eventDefinitions[0].messageRef
   *  - signalName：eventDefinitions[0].signalRef
   *  - timerType / timerValue：eventDefinitions[0] 上挂的 timeDuration / timeDate / timeCycle
   *    任一子元素的 body
   *
   * messageRef / signalRef 指向的 bpmn:Message / bpmn:Signal 维护在
   *  definitions.rootElements。按 name 查重，找到复用，找不到新建，避免冗余声明。
   * 删除节点不会主动清理 rootElements（避免误删被其他节点引用的同名 Message），
   * 运行时 Camunda 按 name correlate，孤儿声明不影响功能。
   * ======================================================================== */

  private getEventValue(element: Element, key: string): any {
    const bo: any = element?.businessObject;
    if (!bo) return undefined;
    const def: any = (bo.eventDefinitions || [])[0];

    if (key === 'messageName') {
      const ref = bo.messageRef ?? def?.messageRef;
      return ref?.name ?? '';
    }
    if (key === 'signalName') {
      return def?.signalRef?.name ?? '';
    }
    if (key === 'timerType') {
      if (!def) return undefined;
      const types = ['timeDuration', 'timeDate', 'timeCycle'];
      for (const t of types) {
        if (def[t]) return t;
      }
      return undefined;
    }
    if (key === 'timerValue') {
      if (!def) return undefined;
      const types = ['timeDuration', 'timeDate', 'timeCycle'];
      for (const t of types) {
        if (def[t]) return def[t].body ?? '';
      }
      return undefined;
    }
    return undefined;
  }

  private setEventValue(bpmnModeler: BaseViewer, element: Element, key: string, value: any): boolean {
    if (key !== 'messageName' && key !== 'signalName' && key !== 'timerType' && key !== 'timerValue') {
      return false;
    }
    const bo: any = element?.businessObject;
    if (!bo) return true;

    if (key === 'messageName') {
      const trimmed = value == null ? '' : String(value).trim();
      const ref = trimmed ? this.findOrCreateRootElementByName(bpmnModeler, element, 'bpmn:Message', trimmed) : null;
      if (element.type === 'bpmn:ReceiveTask') {
        this.updateProperties(bpmnModeler, element, { messageRef: ref ?? undefined });
      } else {
        const def: any = (bo.eventDefinitions || [])[0];
        if (def) {
          this.updateModdleProperties(bpmnModeler, element, def, { messageRef: ref ?? undefined });
        }
      }
      return true;
    }

    if (key === 'signalName') {
      const trimmed = value == null ? '' : String(value).trim();
      const ref = trimmed ? this.findOrCreateRootElementByName(bpmnModeler, element, 'bpmn:Signal', trimmed) : null;
      const def: any = (bo.eventDefinitions || [])[0];
      if (def) {
        this.updateModdleProperties(bpmnModeler, element, def, { signalRef: ref ?? undefined });
      }
      return true;
    }

    const def: any = (bo.eventDefinitions || [])[0];
    if (!def) return true;

    if (key === 'timerType') {
      const newType = String(value || 'timeDuration');
      const types = ['timeDuration', 'timeDate', 'timeCycle'];
      // 保留现有 body，避免切换类型丢值
      let preservedBody = '';
      for (const t of types) {
        if (def[t]) {
          preservedBody = def[t].body ?? '';
          break;
        }
      }
      const updates: any = {};
      for (const t of types) {
        updates[t] = undefined;
      }
      updates[newType] = this.createElement(bpmnModeler, 'bpmn:FormalExpression', { body: preservedBody });
      this.updateModdleProperties(bpmnModeler, element, def, updates);
      return true;
    }

    if (key === 'timerValue') {
      const body = value == null ? '' : String(value);
      const types = ['timeDuration', 'timeDate', 'timeCycle'];
      let currentType: string | null = null;
      for (const t of types) {
        if (def[t]) {
          currentType = t;
          break;
        }
      }
      if (!currentType) currentType = 'timeDuration';
      const expr = this.createElement(bpmnModeler, 'bpmn:FormalExpression', { body });
      this.updateModdleProperties(bpmnModeler, element, def, { [currentType]: expr });
      return true;
    }

    return true;
  }

  /**
   * 从 element 的 businessObject 向上追溯到 bpmn:Definitions 根节点，
   * 在 rootElements 里按 (type, name) 查同名声明：
   *  - 找到 → 复用其 ref；
   *  - 找不到 → 新建一个并 push 到 rootElements。
   *
   * 用于 messageRef / signalRef 字段：避免文本输入时产生孤儿/冗余 root 声明。
   */
  private findOrCreateRootElementByName(bpmnModeler: BaseViewer, element: Element, type: 'bpmn:Message' | 'bpmn:Signal', name: string): any | null {
    const definitions: any = this.getDefinitionsRoot(element);
    if (!definitions) {
      return null;
    }
    const rootElements: any[] = definitions.rootElements ?? [];
    const existing = rootElements.find((e: any) => e?.$type === type && e?.name === name);
    if (existing) {
      return existing;
    }
    const prefix = type === 'bpmn:Message' ? 'Message' : 'Signal';
    const id = `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
    const created = this.createElement(bpmnModeler, type, { id, name }, definitions);
    this.updateModdleProperties(bpmnModeler, element, definitions, {
      rootElements: [...rootElements, created]
    });
    return created;
  }

  private getDefinitionsRoot(element: Element): any | null {
    let bo: any = element?.businessObject;
    while (bo) {
      if (bo.$type === 'bpmn:Definitions') {
        return bo;
      }
      bo = bo.$parent;
    }
    return null;
  }
}
