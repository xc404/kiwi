declare module 'bpmn-auto-layout' {
  /** 为缺少 BPMNDI 的 BPMN XML 生成布局并返回带图形的 XML */
  export function layoutProcess(xml: string): Promise<string>;
}
