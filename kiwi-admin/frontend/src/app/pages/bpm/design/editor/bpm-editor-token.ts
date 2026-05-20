import BpmnModeler from 'bpmn-js/lib/Modeler';

import type { BpmProcess } from '../../types/bpm-process';

export abstract class BpmEditorToken {
  abstract deploy(): Promise<unknown>;

  abstract save(): Promise<unknown>;

  abstract clearSelection(): void;

  abstract getBpmnId(): string;

  abstract getBpmProcess(): BpmProcess | null;

  /**
   * AI 助手：按组件库 id 追加节点（锚点优先 sourceElementId，否则当前选中，否则根流程）。
   */
  abstract appendComponentForAi(componentId: string, sourceElementId?: string | null): void;

  /**
   * AI / 助手：执行与工具栏一致的白名单命令（见后端 AssistantDesignerTools）。
   */
  abstract runToolbarCommand(command: string, options?: Record<string, unknown>): void;

  bpmnModeler!: BpmnModeler;
}
