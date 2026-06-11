import { Observable } from 'rxjs';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import { Element } from 'bpmn-js/lib/model/Types';

type PaletteItem = {
  title: string;
  icon: string;
  /**
   * 可选：bpmn 事件定义类型（如 `bpmn:MessageEventDefinition`、`bpmn:TimerEventDefinition`、
   * `bpmn:SignalEventDefinition`）。设置后，{@link BpmPallete.createElement} 会在创建出来的
   * IntermediateCatchEvent/IntermediateThrowEvent businessObject 上挂上对应空事件定义，
   * 否则导出 XML 只会是裸 `<bpmn:intermediateCatchEvent />`，运行时 Camunda 无法识别为消息/定时事件。
   */
  eventDefinitionType?: string;
} & {
  [additionalProperties: string]: any;
};

declare interface PaletteGroup {
  group: string;
  palettes: PaletteItem[];
}

/**
 * A palette provider for BPMN 2.0 elements.
 */
declare interface PaletteProvider {
  initElement(bpmnModeler: BpmnModeler, element: Element, item: PaletteItem): void;
  getElementOptions(item: PaletteItem): { type: any; options: any; eventDefinitionType?: string };

  getName(): string;
  getPaletteGroup(): Observable<PaletteGroup[]> | PaletteGroup[];
}

export type { PaletteProvider, PaletteGroup, PaletteItem };
