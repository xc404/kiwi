import { inject, Injectable } from '@angular/core';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import ElementFactory from 'bpmn-js/lib/features/modeling/ElementFactory';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import Create from 'diagram-js/lib/features/create/Create';
import { NzMessageService } from 'ng-zorro-antd/message';
import {
  ComponentDescription,
  ComponentProvider,
} from '../../flow-elements/component-provider';
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

  appendComponentForAi(componentId: string, sourceElementId?: string | null): void {
    const component = this.componentProvider.getComponent(componentId);
    if (!component) {
      this.message.error('组件库中不存在该组件，请刷新组件列表后重试');
      return;
    }
    const registry = this.modeler.get('elementRegistry') as {
      get: (id: string) => Element | undefined;
    };
    const selection = this.modeler.get('selection') as { get: () => Element[] };
    const canvas = this.modeler.get('canvas') as { getRootElement: () => Element };
    let source: Element | undefined;
    if (sourceElementId) {
      source = registry.get(sourceElementId);
    }
    if (!source) {
      const sel = selection.get();
      source = sel?.[0] ?? canvas.getRootElement();
    }
    this.appendComponentFromContextPad(source, component, undefined);
  }

  appendComponentFromContextPad(
    sourceElement: Element,
    component: ComponentDescription,
    event: MouseEvent | undefined,
  ): void {
    const item = this.componentService.convertComponentToPalette(component);
    const { type, options } = this.componentService.getElementOptions(item);
    const businessObject = this.bpmnFactory.create(type, options);
    const shape = this.elementFactory.createShape({ type, businessObject });
    this.componentService.initElement(this.modeler, shape, item);
    const autoPlace = this.modeler.get('autoPlace', false) as
      | { append: (source: Element, newShape: Element) => void }
      | false;
    if (autoPlace) {
      autoPlace.append(sourceElement, shape);
    } else if (event) {
      this.create.start(event, shape, { source: sourceElement });
    }
  }
}
