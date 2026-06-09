import { inject, Injectable } from '@angular/core';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import ElementFactory from 'bpmn-js/lib/features/modeling/ElementFactory';
import type { Element } from 'bpmn-js/lib/model/Types';
import Create from 'diagram-js/lib/features/create/Create';

import { NzMessageService } from 'ng-zorro-antd/message';

import { BpmAppendAnchorRequiredError } from './bpm-append-anchor-required.error';
import { ComponentDescription, ComponentProvider } from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';

/** 上下文菜单 / 组件面板追加业务节点（依赖已初始化的 modeler） */
@Injectable()
export class BpmEditorAppendService {
  private readonly message = inject(NzMessageService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly componentService = inject(ComponentService);

  private modeler!: BpmnModeler;
  private bpmnFactory!: BpmnFactory;
  private create!: Create;
  private elementFactory!: ElementFactory;

  init(modeler: BpmnModeler): void {
    this.modeler = modeler;
    this.bpmnFactory = modeler.get('bpmnFactory');
    this.create = modeler.get('create');
    this.elementFactory = modeler.get('elementFactory');
  }

  /**
   * AI / matchComponent：在画布追加组件。锚点优先级：显式 sourceElementId → 当前单选 → 唯一 StartEvent；
   * 无法唯一确定时抛 {@link BpmAppendAnchorRequiredError}，由对话引导用户选择。
   */
  appendComponentForAi(componentId: string, sourceElementId?: string | null): void {
    if (!this.modeler) {
      throw new Error('画布 modeler 未初始化');
    }
    const component = this.componentProvider.getComponent(componentId);
    if (!component) {
      this.message.error(`组件库中不存在 id「${componentId}」`);
      throw new Error(`unknown componentId: ${componentId}`);
    }
    const source = this.resolveAppendSource(sourceElementId, component.name);
    this.appendComponentFromContextPad(source, component, undefined);
    const canvas = this.modeler.get('canvas') as { zoom: (v: string) => void; resized?: () => void };
    canvas.resized?.();
    canvas.zoom('fit-viewport');
  }

  appendComponentFromContextPad(sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined): void {
    const item = this.componentService.convertComponentToPalette(component);
    const { type, options } = this.componentService.getElementOptions(item);
    const businessObject = this.bpmnFactory.create(type, options);
    const shape = this.elementFactory.createShape({ type, businessObject });
    this.componentService.initElement(this.modeler, shape, item);
    const autoPlace = this.modeler.get('autoPlace', false) as { append: (source: Element, newShape: Element) => void } | false;
    if (autoPlace) {
      autoPlace.append(sourceElement, shape);
    } else if (event) {
      this.create.start(event, shape, { source: sourceElement });
    } else {
      const modeling = this.modeler.get('modeling') as {
        appendShape: (source: Element, shape: Element, position?: { x: number; y: number }) => Element;
      };
      const bounds = sourceElement as Element & { x?: number; y?: number; width?: number };
      const x = (bounds.x ?? 0) + (bounds.width ?? 100) + 80;
      const y = bounds.y ?? 0;
      modeling.appendShape(sourceElement, shape, { x, y });
    }
  }

  private resolveAppendSource(sourceElementId: string | null | undefined, componentName: string): Element {
    const registry = this.modeler.get('elementRegistry') as {
      get: (id: string) => Element | undefined;
      getAll: () => Element[];
    };
    const selection = this.modeler.get('selection') as { get: () => Element[] };

    if (sourceElementId?.trim()) {
      const id = sourceElementId.trim();
      let el = registry.get(id);
      if (!el) {
        const lower = id.toLowerCase();
        el = registry.getAll().find(e => (e.id ?? '').toLowerCase() === lower);
      }
      if (el && this.canAppendFrom(el)) {
        return el;
      }
      throw new Error(`指定的锚点「${id}」不存在或不可追加`);
    }

    const sel = selection.get().filter(e => this.canAppendFrom(e));
    if (sel.length === 1) {
      return sel[0];
    }
    if (sel.length > 1) {
      throw new BpmAppendAnchorRequiredError(componentName, sel.map(e => e.id ?? '').filter(Boolean));
    }

    const startEvents = registry.getAll().filter(e => (e as { type?: string }).type === 'bpmn:StartEvent' && this.canAppendFrom(e));
    if (startEvents.length === 1) {
      return startEvents[0];
    }

    const candidates = registry.getAll().filter(e => this.canAppendFrom(e));
    const ids = candidates.map(e => e.id ?? '').filter(Boolean);
    throw new BpmAppendAnchorRequiredError(componentName, ids.slice(0, 12));
  }

  private canAppendFrom(element: Element): boolean {
    const type = (element as { type?: string }).type;
    if (!type || type === 'label') {
      return false;
    }
    if (type === 'bpmn:EndEvent' || type === 'bpmn:Process' || type === 'bpmn:Collaboration' || type === 'bpmn:Participant' || type === 'bpmn:Lane') {
      return false;
    }
    return type.startsWith('bpmn:');
  }
}
