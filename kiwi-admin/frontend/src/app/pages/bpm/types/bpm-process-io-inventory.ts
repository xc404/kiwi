/** 与后端 {@code BpmProcessIoInventory} 对齐（Jackson 枚举名为 PascalCase）。 */
export type BpmProcessIoValueKind = 'Empty' | 'Literal' | 'Expression';
export type BpmProcessIoDirection = 'Input' | 'Output';

export interface BpmProcessIoParam {
  key: string;
  name?: string;
  required?: boolean;
  direction?: BpmProcessIoDirection;
  filled?: boolean;
  valueKind?: BpmProcessIoValueKind;
  configuredValue?: string | null;
  expressionRefs?: string[];
  satisfiedByUpstream?: boolean;
  processVariable?: string;
}

export interface BpmProcessIoNode {
  nodeId: string;
  name?: string;
  componentId?: string;
  componentName?: string;
  inputs?: BpmProcessIoParam[];
  outputs?: BpmProcessIoParam[];
}

export interface BpmProcessIoStartVariable {
  key: string;
  name?: string;
  description?: string;
  required?: boolean;
  nodeId?: string;
  parameterKey?: string;
}

export interface BpmProcessIoInventory {
  nodes?: BpmProcessIoNode[];
  startVariables?: BpmProcessIoStartVariable[];
  processOutputs?: unknown[];
}
