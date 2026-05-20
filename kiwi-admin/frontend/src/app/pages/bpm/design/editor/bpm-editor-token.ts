import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';

import type { BpmProcess } from '../../types/bpm-process';

export abstract class BpmEditorToken {
  abstract deploy(): Promise<unknown>;

  abstract save(): Promise<unknown>;

  abstract clearSelection(): void;

  abstract getBpmnId(): string;

  abstract getBpmProcess(): BpmProcess | null;

  /** 导入 BPMN 到画布（不写入服务器） */
  abstract importBpmnXml(xml: string): Promise<void>;

  /** AI：导入 BPMN 并保存到当前流程定义 */
  abstract importBpmnXmlAndSave(xml: string): Promise<void>;

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
