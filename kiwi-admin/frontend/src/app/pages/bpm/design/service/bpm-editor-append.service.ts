import { inject, Injectable } from '@angular/core';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import ElementFactory from 'bpmn-js/lib/features/modeling/ElementFactory';
import type { Element } from 'bpmn-js/lib/model/Types';
import Create from 'diagram-js/lib/features/create/Create';

import { ComponentDescription } from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';

/** 上下文菜单 / 组件面板追加业务节点（依赖已初始化的 modeler） */
@Injectable()
export class BpmEditorAppendService {
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
}
