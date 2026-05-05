/** 节点运行时变量项（与属性面板注入习惯一致） */
export type BpmnRuntimeVariable = {
    name?: string | null;
    value?: unknown;
    [key: string]: unknown;
};
