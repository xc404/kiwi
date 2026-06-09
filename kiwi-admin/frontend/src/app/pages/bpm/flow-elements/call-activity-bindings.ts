import { Element } from 'bpmn-js/lib/model/Types';

import { ElementModel } from '../design/extension/element-model';

/** CallActivity 走「选择流程」路径（无组件库 componentId） */
export function isCallActivityProcessPick(element: Element, elementModel: ElementModel): boolean {
  if (element.type !== 'bpmn:CallActivity') {
    return false;
  }
  const componentId = elementModel.getValue(undefined as any, element, 'element', 'componentId');
  return componentId == null || String(componentId).trim() === '';
}
