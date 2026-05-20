import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';

import type { BpmProcess } from '../../types/bpm-process';

export abstract class BpmEditorToken {
  abstract deploy(): Promise<unknown>;

  abstract save(): Promise<unknown>;

  abstract clearSelection(): void;

  abstract getBpmnId(): string;

  abstract getBpmProcess(): BpmProcess | null;

  /** AI / 导入：替换当前画布 BPMN（未保存到服务器） */
  abstract importBpmnXml(xml: string): Promise<void>;

  bpmnModeler!: BpmnModeler;

  getSelectedElementId(): string | null {
    if (!this.bpmnModeler) {
      return null;
    }
    const selection = this.bpmnModeler.get('selection') as { get: () => Element[] };
    const selected = selection.get?.() ?? [];
    if (selected.length !== 1) {
      return null;
    }
    return selected[0].id ?? null;
  }
}
