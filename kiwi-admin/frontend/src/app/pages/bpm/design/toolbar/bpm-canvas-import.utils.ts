import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { NzMessageService } from 'ng-zorro-antd/message';

export type BpmCanvasImportOptions = {
  /** 默认 true；AI 导入后链式保存时设为 false，避免提示「尚未保存」 */
  notifySuccess?: boolean;
};

export function importBpmnXmlToModeler(
  modeler: BpmnModeler,
  xml: string,
  message: NzMessageService,
  clearSelection: () => void,
  options?: BpmCanvasImportOptions,
): Promise<void> {
  const notifySuccess = options?.notifySuccess !== false;
  const trimmed = typeof xml === 'string' ? xml.trim() : '';
  if (!trimmed) {
    message.warning('BPMN XML 为空');
    return Promise.reject(new Error('empty xml'));
  }
  return modeler
    .importXML(trimmed)
    .then(({ warnings }) => {
      const canvas = modeler.get('canvas') as { zoom: (v: string) => void };
      canvas.zoom('fit-viewport');
      clearSelection();
      if (warnings?.length) {
        console.warn('BPMN import warnings:', warnings);
        message.warning(
          `已导入，存在 ${warnings.length} 条解析提示（详见控制台），请检查后保存`,
        );
      } else if (notifySuccess) {
        message.success('已导入 BPMN XML，尚未保存到服务器');
      }
    })
    .catch((err: unknown) => {
      const msg =
        err && typeof err === 'object' && 'message' in err
          ? String((err as { message: unknown }).message)
          : String(err);
      message.error(`导入失败：${msg}`);
      return Promise.reject(err);
    });
}
