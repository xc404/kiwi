import BpmnModeler from 'bpmn-js/lib/Modeler';

import type {
  BpmSaveAsComponentModalData,
  SaveAsComponentFormPayload,
} from '../toolbar/bpm-save-as-component-modal/bpm-save-as-component-modal.component';

export abstract class BpmEditorToken {
  abstract deploy(): Promise<unknown>;
  /**
   * 启动流程。可传入流程变量对象，或 JSON 字符串；不传则读取该流程在 localStorage 中的上次变量。
   * 启动成功后会按 bpmnId 缓存 variables，供下次调用或弹窗预填。
   */
  abstract start(variables?: Record<string, unknown> | string): Promise<unknown>;

  abstract save(): Promise<unknown>;

  abstract clearSelection(): void;

  /** 另存为组件弹窗：默认表单数据（由 Toolbar 打开 modal 时作为 nzData） */
  abstract getSaveAsComponentModalDefaults(): BpmSaveAsComponentModalData;

  /** 另存为组件：部署后提交表单（由 Toolbar modal 的 nzOnOk 调用） */
  abstract submitSaveAsComponent(payload: SaveAsComponentFormPayload): Promise<void>;

  /** 启动流程弹窗：变量 JSON 编辑器初始文本 */
  abstract getStartProcessModalInitialText(): string;

  /** 启动流程：部署并带变量调引擎（由 Toolbar modal 的 nzOnOk 调用） */
  abstract submitStartProcessFromModal(variables: Record<string, unknown>): Promise<void>;

  /** 用 BPMN 2.0 XML 替换当前画布内容（不自动保存到服务端）。 */
  abstract importBpmnXml(xml: string): Promise<void>;

  /**
   * AI 助手：按组件库 id 追加节点（锚点优先 sourceElementId，否则当前选中，否则根流程）。
   */
  abstract appendComponentForAi(componentId: string, sourceElementId?: string | null): void;

  bpmnModeler!: BpmnModeler;
}
