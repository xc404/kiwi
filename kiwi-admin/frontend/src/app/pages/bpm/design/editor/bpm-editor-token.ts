import BpmnModeler from 'bpmn-js/lib/Modeler';

export abstract class BpmEditorToken {
  abstract deploy(): void;
  abstract start(): void;
  abstract save(): void;

  abstract clearSelection(): void;

  /** 将当前图另存为组件库中的组件（输入/输出由服务端分析 BPMN 推导） */
  abstract saveAsComponent(): void;

  /**
   * AI 助手：按组件库 id 追加节点（锚点优先 sourceElementId，否则当前选中，否则根流程）。
   */
  abstract appendComponentForAi(componentId: string, sourceElementId?: string | null): void;

  bpmnModeler!: BpmnModeler;
}
