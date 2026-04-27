import BpmnModeler from 'bpmn-js/lib/Modeler';

export abstract class BpmEditorToken {
  abstract deploy(): void;
  /**
   * 启动流程。可传入流程变量对象，或 JSON 字符串；不传则读取该流程在 localStorage 中的上次变量。
   * 启动成功后会按 bpmnId 缓存 variables，供下次调用或弹窗预填。
   */
  abstract start(variables?: Record<string, unknown> | string): Promise<unknown>;

  /** 打开「启动流程变量」编辑弹窗（预填缓存 JSON）。 */
  abstract openStartProcessDialog(): void;
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
