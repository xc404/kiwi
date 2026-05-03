import { Component, ElementRef, inject, input, ViewChild } from '@angular/core';
import { saveAs } from 'file-saver';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';
import { BpmEditorToken } from '../editor/bpm-editor-token';
import { BpmSaveAsComponentModalComponent } from './bpm-save-as-component-modal/bpm-save-as-component-modal.component';
import { BpmStartProcessModalComponent } from './bpm-start-process-modal/bpm-start-process-modal.component';

type ToolbarButtonConfig = {
  tooltip: string;
  icon: string;
  onClick: () => void;
};

type ToolbarSegment =
  | { type: 'divider' }
  | { type: 'group'; buttons: ToolbarButtonConfig[] };

@Component({
  selector: 'bpm-toolbar',
  templateUrl: './bpm-toolbar.html',
  styleUrls: ['bpm-toolbar.css'],
  standalone: true,
  imports: [NzButtonModule, NzIconModule, NzTooltipModule, NzDividerModule],
})
export class BpmToolbar {
  private readonly bpmnEditor = inject(BpmEditorToken);
  private readonly message = inject(NzMessageService);
  private readonly modalWrap = inject(NzModalWrapService);

  @ViewChild('bpmnFileInput') private bpmnFileInput?: ElementRef<HTMLInputElement>;

  bpmnModeler = input.required<BpmnModeler>();

  readonly toolbarSegments: ToolbarSegment[];

  constructor() {
    this.toolbarSegments = [
      {
        type: 'group',
        buttons: [
          { tooltip: '套索选择', icon: 'border', onClick: () => this.triggerEditorAction('lassoTool') },
          { tooltip: '平移画布（手型）', icon: 'drag', onClick: () => this.triggerEditorAction('handTool') },
          { tooltip: '全局连线', icon: 'link', onClick: () => this.triggerEditorAction('globalConnectTool') },
          { tooltip: '空间工具', icon: 'column-width', onClick: () => this.triggerEditorAction('spaceTool') },
        ],
      },
      { type: 'divider' },
      {
        type: 'group',
        buttons: [
          { tooltip: '撤销', icon: 'undo', onClick: () => this.onUndo() },
          { tooltip: '重做', icon: 'redo', onClick: () => this.onRedo() },
          { tooltip: '复制', icon: 'copy', onClick: () => this.onCopy() },
          { tooltip: '粘贴', icon: 'snippets', onClick: () => this.onPaste() },
          { tooltip: '删除', icon: 'delete', onClick: () => this.onRemoveSelection() },
        ],
      },
      { type: 'divider' },
      {
        type: 'group',
        buttons: [
          { tooltip: '放大', icon: 'zoom-in', onClick: () => this.onZoomIn() },
          { tooltip: '缩小', icon: 'zoom-out', onClick: () => this.onZoomOut() },
          { tooltip: '适应画布', icon: 'expand', onClick: () => this.onZoomFit() },
          { tooltip: '搜索元素', icon: 'search', onClick: () => this.onFind() },
          { tooltip: '切换网格吸附', icon: 'appstore', onClick: () => this.toggleGridSnapping() },
        ],
      },
      { type: 'divider' },
      {
        type: 'group',
        buttons: [
          { tooltip: '保存', icon: 'save', onClick: () => this.onSave() },
          { tooltip: '另存为组件', icon: 'appstore-add', onClick: () => this.onSaveAsComponent() },
          {
            tooltip: '从文件导入 BPMN XML（覆盖当前图，需再保存）',
            icon: 'upload',
            onClick: () => this.onImportXmlClick(),
          },
          { tooltip: '下载 BPMN XML', icon: 'file-text', onClick: () => this.onExportXml() },
          { tooltip: '下载 SVG', icon: 'file-image', onClick: () => this.onExportSvg() },
          { tooltip: '控制台输出 XML', icon: 'code', onClick: () => this.onLogXml() },
          { tooltip: '部署', icon: 'cloud-upload', onClick: () => this.onDeploy() },
          { tooltip: '启动流程', icon: 'play-circle', onClick: () => this.onStart() },
        ],
      },
    ];
  }

  triggerEditorAction(action: string, opts?: Record<string, unknown>): void {
    const ea = this.bpmnModeler().get('editorActions') as {
      isRegistered: (a: string) => boolean;
      trigger: (a: string, o?: Record<string, unknown>) => void;
    };
    if (ea?.isRegistered?.(action)) {
      ea.trigger(action, opts);
    }
  }

  toggleGridSnapping(): void {
    const gs = this.bpmnModeler().get('gridSnapping') as { toggleActive?: () => void } | undefined;
    gs?.toggleActive?.();
  }

  onUndo(): void {
    this.triggerEditorAction('undo');
  }

  onRedo(): void {
    this.triggerEditorAction('redo');
  }

  onCopy(): void {
    this.triggerEditorAction('copy');
  }

  onPaste(): void {
    this.triggerEditorAction('paste');
  }

  onRemoveSelection(): void {
    this.triggerEditorAction('removeSelection');
  }

  onZoomIn(): void {
    this.triggerEditorAction('stepZoom', { value: 1 });
  }

  onZoomOut(): void {
    this.triggerEditorAction('stepZoom', { value: -1 });
  }

  onZoomFit(): void {
    this.triggerEditorAction('zoom', { value: 'fit-viewport' });
  }

  onFind(): void {
    this.triggerEditorAction('find');
  }

  onSave(): void {
    this.bpmnEditor.save();
  }

  onSaveAsComponent(): void {
    const ref = this.modalWrap.create({
      nzTitle: '另存为组件',
      nzWidth: 520,
      nzOkText: '保存到组件库',
      nzCancelText: '取消',
      nzContent: BpmSaveAsComponentModalComponent,
      nzData: this.bpmnEditor.getSaveAsComponentModalDefaults(),
      nzOnOk: () => {
        const comp = ref.getContentComponent() as BpmSaveAsComponentModalComponent;
        const payload = comp.tryGetPayload();
        if (!payload) {
          return false;
        }
        return this.bpmnEditor.submitSaveAsComponent(payload);
      },
    });
  }

  onDeploy(): void {
    void this.bpmnEditor.deploy();
  }

  onStart(): void {
    const ref = this.modalWrap.create({
      nzTitle: '启动流程变量',
      nzWidth: 560,
      nzOkText: '部署并启动',
      nzCancelText: '取消',
      nzContent: BpmStartProcessModalComponent,
      nzData: { initialText: this.bpmnEditor.getStartProcessModalInitialText() },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as BpmStartProcessModalComponent;
        const parsed = comp.parseVariablesOrFalse();
        if (parsed === false) {
          return false;
        }
        return this.bpmnEditor
          .submitStartProcessFromModal(parsed)
          .then(() => {
            this.message.success('流程已启动');
          })
          .catch((err: unknown) => {
            const e = err as { error?: { message?: string }; message?: string };
            this.message.error(e?.error?.message ?? e?.message ?? '启动失败');
            return Promise.reject(err);
          });
      },
    });
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
      void this.bpmnEditor.importBpmnXml(text).catch(() => {
        /* 错误已在 editor 内提示 */
      });
    };
    reader.onerror = () => {
      this.message.error('读取文件失败');
    };
    reader.readAsText(file, 'utf-8');
    input.value = '';
  }

  onExportXml(): void {
    void this.bpmnModeler()
      .saveXML({ format: true })
      .then((result) => {
        const xml = result.xml;
        if (!xml) {
          this.message.error('导出 XML 失败');
          return;
        }
        const blob = new Blob([xml], { type: 'application/bpmn20-xml;charset=utf-8' });
        saveAs(blob, 'diagram.bpmn');
        this.message.success('已下载 BPMN XML');
      })
      .catch(() => {
        this.message.error('导出 XML 失败');
      });
  }

  onExportSvg(): void {
    void this.bpmnModeler()
      .saveSVG()
      .then((result) => {
        const svg = result.svg;
        if (!svg) {
          this.message.error('导出 SVG 失败');
          return;
        }
        const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
        saveAs(blob, 'diagram.svg');
        this.message.success('已下载 SVG');
      })
      .catch(() => {
        this.message.error('导出 SVG 失败');
      });
  }

  onLogXml(): void {
    void this.bpmnModeler()
      .saveXML({ format: true })
      .then((result) => {
        if (result.xml) {
          console.log(result.xml);
          this.message.info('已在控制台输出 XML');
        }
      });
  }
}
