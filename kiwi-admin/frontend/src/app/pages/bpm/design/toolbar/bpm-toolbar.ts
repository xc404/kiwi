import { Component, ElementRef, inject, input, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';
import { firstValueFrom } from 'rxjs';

import { BpmEditorToken } from '../editor/bpm-editor-token';
import { ProcessDesignService } from '../service/process-design.service';
import { BpmStartVariablesService } from '../service/bpm-start-variables.service';
import { buildToolbarSegments, type ToolbarSegment } from './build-toolbar-segments';
import { importBpmnXmlToModeler } from './bpm-canvas-import.utils';
import { BpmDesignerToolbarService } from './bpm-designer-toolbar.service';
import type {
  BpmDesignerToolbarCommand,
  BpmDesignerToolbarContext,
} from './bpm-designer-toolbar.types';
import type {
  BpmSaveAsComponentModalData,
  SaveAsComponentFormPayload,
} from './bpm-save-as-component-modal/bpm-save-as-component-modal.component';
import {
  exportBpmnSvg,
  exportBpmnXml,
  logBpmnXml,
  openSaveAsComponentModal,
  openStartProcessModal,
  toggleGridSnapping,
  triggerEditorAction,
} from './bpm-toolbar-run.utils';

@Component({
  selector: 'bpm-toolbar',
  templateUrl: './bpm-toolbar.html',
  styleUrls: ['bpm-toolbar.css'],
  standalone: true,
  imports: [NzButtonModule, NzIconModule, NzTooltipModule, NzDividerModule],
})
export class BpmToolbar implements BpmDesignerToolbarContext {
  readonly editor = inject(BpmEditorToken);
  readonly message = inject(NzMessageService);
  readonly modalWrap = inject(NzModalWrapService);
  readonly router = inject(Router);
  private readonly processDefinitionService = inject(ProcessDesignService);
  private readonly startVariables = inject(BpmStartVariablesService);
  private readonly toolbarService = inject(BpmDesignerToolbarService);

  @ViewChild('bpmnFileInput') private bpmnFileInput?: ElementRef<HTMLInputElement>;

  bpmnModeler = input.required<BpmnModeler>();

  readonly openImportFile = (): void => this.onImportXmlClick();

  readonly toolbarSegments: ToolbarSegment[];

  constructor() {
    this.registerCommands();
    this.toolbarSegments = buildToolbarSegments(
      this.toolbarService.listUiCommands(),
      (id) => this.runCommand(id),
    );
  }

  get modeler(): BpmnModeler {
    return this.bpmnModeler();
  }

  onImportXmlClick(): void {
    this.bpmnFileInput?.nativeElement.click();
  }

  onBpmnFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      void this.importBpmnXml(text).catch(() => {
        /* 错误已在 import 内提示 */
      });
    };
    reader.onerror = () => {
      this.message.error('读取文件失败');
    };
    reader.readAsText(file, 'utf-8');
    input.value = '';
  }

  importBpmnXml(xml: string): Promise<void> {
    return importBpmnXmlToModeler(this.modeler, xml, this.message, () => this.editor.clearSelection());
  }

  getSaveAsComponentModalDefaults(): BpmSaveAsComponentModalData {
    const process = this.editor.getBpmProcess();
    return {
      defaultName: process?.name || '流程',
      defaultDescription: '',
      defaultVersion: '1.0',
    };
  }

  submitSaveAsComponent(payload: SaveAsComponentFormPayload): Promise<void> {
    return this.editor
      .deploy()
      .then(() => {
        const processId = this.editor.getBpmProcess()?.id as string;
        if (!processId) {
          this.message.error('流程未加载');
          return Promise.reject(new Error('no process'));
        }
        return firstValueFrom(
          this.processDefinitionService.saveAsComponent(processId, {
            name: payload.name,
            description: payload.description,
            version: payload.version,
          }),
        );
      })
      .then(() => {
        this.message.success('已保存到组件库');
      })
      .catch((err: unknown) => {
        const e = err as { error?: { message?: string }; message?: string };
        const msg = e?.error?.message || e?.message || '保存失败';
        this.message.error(msg);
        return Promise.reject(err);
      });
  }

  getStartProcessModalInitialText(): string {
    const id = this.editor.getBpmnId();
    const cached = id ? this.startVariables.load(id) : undefined;
    return cached !== undefined ? JSON.stringify(cached, null, 2) : '{}';
  }

  submitStartProcessFromModal(variables: Record<string, unknown>): Promise<unknown> {
    return this.startProcessWithVariables(variables);
  }

  private startProcessWithVariables(variables: Record<string, unknown>): Promise<unknown> {
    const id = this.editor.getBpmnId();
    if (!id) {
      this.message.error('流程 ID 不存在');
      return Promise.reject(new Error('no bpmn id'));
    }
    this.startVariables.persist(id, variables);
    return this.editor.deploy().then(
      () =>
        new Promise<unknown>((resolve, reject) => {
          this.processDefinitionService.startProcess(id, { variables }).subscribe({
            next: (data: unknown) => resolve(data),
            error: (err: unknown) => reject(err),
          });
        }),
    );
  }

  private registerCommands(): void {
    const cmds: BpmDesignerToolbarCommand[] = [
      {
        id: 'lassoTool',
        tooltip: '套索选择',
        icon: 'border',
        group: 'tools',
        aiExposed: false,
        run: (ctx) => triggerEditorAction(ctx, 'lassoTool'),
      },
      {
        id: 'handTool',
        tooltip: '平移画布（手型）',
        icon: 'drag',
        group: 'tools',
        aiExposed: false,
        run: (ctx) => triggerEditorAction(ctx, 'handTool'),
      },
      {
        id: 'globalConnectTool',
        tooltip: '全局连线',
        icon: 'link',
        group: 'tools',
        aiExposed: false,
        run: (ctx) => triggerEditorAction(ctx, 'globalConnectTool'),
      },
      {
        id: 'spaceTool',
        tooltip: '空间工具',
        icon: 'column-width',
        group: 'tools',
        aiExposed: false,
        run: (ctx) => triggerEditorAction(ctx, 'spaceTool'),
      },
      {
        id: 'undo',
        tooltip: '撤销',
        icon: 'undo',
        group: 'edit',
        run: (ctx) => triggerEditorAction(ctx, 'undo'),
      },
      {
        id: 'redo',
        tooltip: '重做',
        icon: 'redo',
        group: 'edit',
        run: (ctx) => triggerEditorAction(ctx, 'redo'),
      },
      {
        id: 'copy',
        tooltip: '复制',
        icon: 'copy',
        group: 'edit',
        run: (ctx) => triggerEditorAction(ctx, 'copy'),
      },
      {
        id: 'paste',
        tooltip: '粘贴',
        icon: 'snippets',
        group: 'edit',
        run: (ctx) => triggerEditorAction(ctx, 'paste'),
      },
      {
        id: 'removeSelection',
        tooltip: '删除',
        icon: 'delete',
        group: 'edit',
        run: (ctx) => triggerEditorAction(ctx, 'removeSelection'),
      },
      {
        id: 'zoomIn',
        tooltip: '放大',
        icon: 'zoom-in',
        group: 'view',
        run: (ctx) => triggerEditorAction(ctx, 'stepZoom', { value: 1 }),
      },
      {
        id: 'zoomOut',
        tooltip: '缩小',
        icon: 'zoom-out',
        group: 'view',
        run: (ctx) => triggerEditorAction(ctx, 'stepZoom', { value: -1 }),
      },
      {
        id: 'zoomFit',
        tooltip: '适应画布',
        icon: 'expand',
        group: 'view',
        run: (ctx) => triggerEditorAction(ctx, 'zoom', { value: 'fit-viewport' }),
      },
      {
        id: 'find',
        tooltip: '搜索元素',
        icon: 'search',
        group: 'view',
        run: (ctx) => triggerEditorAction(ctx, 'find'),
      },
      {
        id: 'gridSnapping',
        tooltip: '切换网格吸附',
        icon: 'appstore',
        group: 'view',
        aiExposed: false,
        run: (ctx) => toggleGridSnapping(ctx),
      },
      {
        id: 'save',
        tooltip: '保存',
        icon: 'save',
        group: 'file',
        run: (ctx) => {
          void ctx.editor.save();
        },
      },
      {
        id: 'saveAsComponent',
        tooltip: '另存为组件',
        icon: 'appstore-add',
        group: 'file',
        run: (ctx) => openSaveAsComponentModal(ctx),
      },
      {
        id: 'importXml',
        tooltip: '从文件导入 BPMN XML（覆盖当前图，需再保存）',
        icon: 'upload',
        group: 'file',
        aiExposed: false,
        run: (ctx) => ctx.openImportFile?.(),
      },
      {
        id: 'exportXml',
        tooltip: '下载 BPMN XML',
        icon: 'file-text',
        group: 'file',
        run: (ctx) => exportBpmnXml(ctx),
      },
      {
        id: 'exportSvg',
        tooltip: '下载 SVG',
        icon: 'file-image',
        group: 'file',
        run: (ctx) => exportBpmnSvg(ctx),
      },
      {
        id: 'logXml',
        tooltip: '控制台输出 XML',
        icon: 'code',
        group: 'file',
        aiExposed: false,
        run: (ctx) => logBpmnXml(ctx),
      },
      {
        id: 'deploy',
        tooltip: '部署',
        icon: 'cloud-upload',
        group: 'file',
        run: (ctx) => {
          void ctx.editor.deploy();
        },
      },
      {
        id: 'start',
        tooltip: '启动流程',
        icon: 'play-circle',
        group: 'file',
        run: (ctx) => openStartProcessModal(ctx),
      },
    ];
    this.toolbarService.registerAll(cmds);
  }

  private runCommand(id: string): void {
    this.toolbarService.run(id, this);
  }
}
