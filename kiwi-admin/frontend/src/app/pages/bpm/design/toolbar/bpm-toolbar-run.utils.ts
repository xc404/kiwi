import { saveAs } from 'file-saver';

import type { BpmDesignerToolbarContext } from './bpm-designer-toolbar.types';
import { BpmSaveAsComponentModalComponent } from './bpm-save-as-component-modal/bpm-save-as-component-modal.component';
import { BpmStartProcessModalComponent } from './bpm-start-process-modal/bpm-start-process-modal.component';

export function triggerEditorAction(
  ctx: BpmDesignerToolbarContext,
  action: string,
  opts?: Record<string, unknown>,
): void {
  const ea = ctx.modeler.get('editorActions') as {
    isRegistered: (a: string) => boolean;
    trigger: (a: string, o?: Record<string, unknown>) => void;
  };
  if (ea?.isRegistered?.(action)) {
    ea.trigger(action, opts);
  }
}

export function toggleGridSnapping(ctx: BpmDesignerToolbarContext): void {
  const gs = ctx.modeler.get('gridSnapping') as { toggleActive?: () => void } | undefined;
  gs?.toggleActive?.();
}

export function openSaveAsComponentModal(ctx: BpmDesignerToolbarContext): void {
  const ref = ctx.modalWrap.create({
    nzTitle: '另存为组件',
    nzWidth: 520,
    nzOkText: '保存到组件库',
    nzCancelText: '取消',
    nzContent: BpmSaveAsComponentModalComponent,
    nzData: ctx.getSaveAsComponentModalDefaults(),
    nzOnOk: () => {
      const comp = ref.getContentComponent() as BpmSaveAsComponentModalComponent;
      const payload = comp.tryGetPayload();
      if (!payload) {
        return false;
      }
      return ctx.submitSaveAsComponent(payload);
    },
  });
}

export function openStartProcessModal(ctx: BpmDesignerToolbarContext): void {
  const ref = ctx.modalWrap.create({
    nzTitle: '启动流程变量',
    nzWidth: 560,
    nzOkText: '部署并启动',
    nzCancelText: '取消',
    nzContent: BpmStartProcessModalComponent,
    nzData: { initialText: ctx.getStartProcessModalInitialText() },
    nzOnOk: () => {
      const comp = ref.getContentComponent() as BpmStartProcessModalComponent;
      const parsed = comp.parseVariablesOrFalse();
      if (parsed === false) {
        return false;
      }
      return ctx
        .submitStartProcessFromModal(parsed)
        .then((started) => {
          ctx.message.success('流程已启动');
          promptOpenProcessInstanceViewer(ctx, started);
        })
        .catch((err: unknown) => {
          const e = err as { error?: { message?: string }; message?: string };
          ctx.message.error(e?.error?.message ?? e?.message ?? '启动失败');
          return Promise.reject(err);
        });
    },
  });
}

function promptOpenProcessInstanceViewer(ctx: BpmDesignerToolbarContext, startResult: unknown): void {
  const dto = startResult != null && typeof startResult === 'object' ? (startResult as Record<string, unknown>) : null;
  const idRaw = dto?.['id'];
  const id = idRaw != null && String(idRaw).trim() !== '' ? String(idRaw) : '';

  const lines: string[] = [];
  if (id) {
    lines.push(`实例 ID：${id}`);
  }
  const defIdRaw = dto?.['definitionId'];
  if (defIdRaw != null && String(defIdRaw).trim() !== '') {
    lines.push(`流程定义 ID：${String(defIdRaw)}`);
  }
  const bkRaw = dto?.['businessKey'];
  if (bkRaw != null && String(bkRaw).trim() !== '') {
    lines.push(`业务键：${String(bkRaw)}`);
  }
  const body = lines.length > 0 ? lines.join('\n') : JSON.stringify(startResult ?? {}, null, 2);

  ctx.modalWrap.confirm({
    nzTitle: '流程实例已创建',
    nzWidth: 560,
    nzContent: body,
    nzBodyStyle: { whiteSpace: 'pre-wrap', wordBreak: 'break-all' },
    nzOkText: '查看流程实例',
    nzCancelText: '关闭',
    nzOnOk: () => {
      if (!id) {
        ctx.message.warning('未返回实例 ID，无法打开查看器');
        return;
      }
      const url = new URL(window.location.href);
      url.hash = ctx.router.serializeUrl(
        ctx.router.createUrlTree(['/bpm/process-instance', id]),
      );
      window.open(url.toString(), '_blank', 'noopener,noreferrer');
    },
  });
}

export function exportBpmnXml(ctx: BpmDesignerToolbarContext): void {
  void ctx.modeler.saveXML({ format: true }).then((result) => {
    const xml = result.xml;
    if (!xml) {
      ctx.message.error('导出 XML 失败');
      return;
    }
    const blob = new Blob([xml], { type: 'application/bpmn20-xml;charset=utf-8' });
    saveAs(blob, 'diagram.bpmn');
    ctx.message.success('已下载 BPMN XML');
  });
}

export function exportBpmnSvg(ctx: BpmDesignerToolbarContext): void {
  void ctx.modeler.saveSVG().then((result) => {
    const svg = result.svg;
    if (!svg) {
      ctx.message.error('导出 SVG 失败');
      return;
    }
    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    saveAs(blob, 'diagram.svg');
    ctx.message.success('已下载 SVG');
  });
}

export function logBpmnXml(ctx: BpmDesignerToolbarContext): void {
  void ctx.modeler.saveXML({ format: true }).then((result) => {
    if (result.xml) {
      console.log(result.xml);
      ctx.message.info('已在控制台输出 XML');
    }
  });
}
