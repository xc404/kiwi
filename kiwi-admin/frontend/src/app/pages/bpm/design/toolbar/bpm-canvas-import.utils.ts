import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { NzMessageService } from 'ng-zorro-antd/message';

export function importBpmnXmlToModeler(
  modeler: BpmnModeler,
  xml: string,
  message: NzMessageService,
  clearSelection: () => void,
): Promise<void> {
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
      } else {
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
