import type { Router } from '@angular/router';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { NzMessageService } from 'ng-zorro-antd/message';

import type { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import type { BpmEditorToken } from '../editor/bpm-editor-token';
import type {
  BpmSaveAsComponentModalData,
  SaveAsComponentFormPayload,
} from './bpm-save-as-component-modal/bpm-save-as-component-modal.component';

/** 与后端 AssistantDesignerTools.DEFAULT_TOOLBAR_COMMANDS 对齐 */
export const BPM_AI_TOOLBAR_COMMAND_IDS = [
  'undo',
  'redo',
  'copy',
  'paste',
  'removeSelection',
  'find',
  'zoomIn',
  'zoomOut',
  'zoomFit',
  'save',
  'deploy',
  'start',
  'saveAsComponent',
  'exportXml',
  'exportSvg',
] as const;

export type BpmDesignerToolbarGroup = 'tools' | 'edit' | 'view' | 'file';

export interface BpmDesignerToolbarContext {
  editor: BpmEditorToken;
  modeler: BpmnModeler;
  message: NzMessageService;
  modalWrap: NzModalWrapService;
  router: Router;
  /** 由 Toolbar 注入：打开本地 BPMN 文件选择 */
  openImportFile?: () => void;

  getSaveAsComponentModalDefaults(): BpmSaveAsComponentModalData;
  submitSaveAsComponent(payload: SaveAsComponentFormPayload): Promise<void>;
  getStartProcessModalInitialText(): string;
  submitStartProcessFromModal(variables: Record<string, unknown>): Promise<unknown>;
  importBpmnXml(xml: string): Promise<void>;
}

export interface BpmDesignerToolbarCommand {
  id: string;
  tooltip: string;
  icon: string;
  group: BpmDesignerToolbarGroup;
  run: (ctx: BpmDesignerToolbarContext, options?: Record<string, unknown>) => void | Promise<void>;
  /** 默认 true：AI 助手可通过 toolbar service 执行 */
  aiExposed?: boolean;
  /** 默认 true：出现在 Toolbar 按钮 */
  showInToolbar?: boolean;
}
