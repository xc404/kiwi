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

  /** Agent 预览：导入候选 BPMN（无成功提示，可 undo 回退） */
  abstract importBpmnXmlForPreview(xml: string): Promise<void>;

  /** AI：导入 BPMN 并保存到当前流程定义 */
  abstract importBpmnXmlAndSave(xml: string): Promise<void>;

  /** 撤销：优先 commandStack，否则恢复最近一次整图 import 前快照 */
  abstract undo(): void;

  /** 重做：优先 commandStack，否则恢复整图 import redo 栈 */
  abstract redo(): void;

  /** 确认保存 Agent 预览：提交当前画布为正式基线，丢弃预览 undo 快照 */
  abstract commitAgentPreviewSave(canvasBpmnXml: string): Promise<void>;

  /** Agent 候选 BPMN 已导入画布，等待用户确认保存或拒绝 */
  abstract setAgentPreviewActive(active: boolean): void;

  /** 预览导入后刷新画布布局 */
  abstract refreshCanvasAfterPreview(): void;

  /** 读取当前画布 BPMN XML（含用户手动改图） */
  abstract captureCurrentBpmnXml(): Promise<string>;

  /** 拒绝 Agent 预览：整图回退到导入预览前的快照（非单步 undo） */
  abstract rejectAgentPreview(): Promise<void>;

  /** 左侧面板 Tab，供 Agent 面板在可见时刷新持久化会话 */
  abstract getLeftPanelTab(): 'palette' | 'agent';

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
