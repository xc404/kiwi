import { Component, inject, input } from '@angular/core';
import { saveAs } from 'file-saver';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { BpmEditorToken } from '../editor/bpm-editor';

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

  bpmnModeler = input.required<BpmnModeler>();

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

  onDeploy(): void {
    void this.bpmnEditor.deploy();
  }

  onStart(): void {
    void this.bpmnEditor.start();
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
