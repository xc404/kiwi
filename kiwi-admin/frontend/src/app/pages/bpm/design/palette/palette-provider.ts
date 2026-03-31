import  BpmnModeler  from 'bpmn-js/lib/Modeler';
import { Element } from 'bpmn-js/lib/model/Types';
import { Observable } from "rxjs";


type PaletteItem = {
  title: string;
  icon: string;
} & {
  [additionalProperties: string]: any;
}

declare interface PaletteGroup {
  group: string;
  palettes: PaletteItem[];
}


/**
 * A palette provider for BPMN 2.0 elements.
 */
declare interface PaletteProvider {
  initElement(bpmnModeler: BpmnModeler, element: Element, item: PaletteItem): void;
  getElementOptions(item: PaletteItem): { type: any; options: any; };

  getName(): string;
  getPaletteGroup(): Observable<PaletteGroup[]> | PaletteGroup[];
}


export type { PaletteProvider, PaletteGroup, PaletteItem };



