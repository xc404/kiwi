import { FieldEditorConfig } from "@app/shared/components/field/field-editor";
import { Element } from "bpmn-js/lib/model/Types";
import { ElementModel } from "../extension/element-model";


enum PropertyNamespace {

    element = "element",
    inputParameter = "inputParameter",
    outputParameter = "outputParameter",
    In = "In",
    Out = "Out"
}



export declare type PropertyDescription = {
    type?: string;
    key: string;
    name?: string;
    description?: string;
    namespace?: PropertyNamespace;
    htmlType?: string;
    defaultValue?: any;
    readonly?: boolean;
    hidden?: boolean;
    example?: any;
    required?: boolean;
    dictKey?: string;
    /** 参数分组，用于属性面板折叠分组名（与输入/输出方向组合展示） */
    group?: string;
    /** 为 false 时在「其他」页签展示；缺省或非 false 时归入按 group 划分的主分组 */
    important?: boolean;
    /**
     * 为 true 时表示该条来自组件目录声明的 outputParameter，仅以只读元数据展示，不经 BPMN getValue/setValue。
     */
    declaredOutputParameter?: boolean;
    /**
     * 为 true 时使用「自定义 Camunda outputParameter」列表编辑器；{@link catalogOutputKeys} 为目录声明 key，用于合并写回 BPMN。
     */
    customCamundaOutputParameterList?: boolean;
    /** 与 `customCamundaOutputParameterList` 配套：目录声明输出的 key 列表 */
    catalogOutputKeys?: string[];
};


/** 合成字段 key：自定义 Camunda OutputParameter 列表（见 formly `bpm-camunda-custom-outputs`） */
export const CAMUNDA_CUSTOM_OUTPUTS_PROPERTY_KEY = "__camundaCustomOutputs";


export type PropertyTab = {
    name?: string;
    groups: { name: string; properties: PropertyDescription[]; important?: boolean }[];
};

export interface PropertyProvider {
    getProperties(element: Element): PropertyTab[];
}



export function toEditFieldConfig(property: PropertyDescription, elementModel?: ElementModel): FieldEditorConfig {
    let editor = property.htmlType;
    const defaultExprEditor = elementModel?.expressionEditorFormlyType() ?? 'spel-expression';
    if (property.declaredOutputParameter) {
        return {
            ...property,
            dataIndex: property.key,
            name: (property.name?.trim() || property.key),
            editor: "bpm-declared-output-catalog",
            readonly: true,
        } as FieldEditorConfig;
    }
    if (property.customCamundaOutputParameterList) {
        return {
            ...property,
            dataIndex: property.key,
            name: property.name || "自定义输出",
            editor: "bpm-camunda-custom-outputs",
        } as FieldEditorConfig;
    }
    if(property.namespace === PropertyNamespace.inputParameter || property.namespace === PropertyNamespace.In) {
        editor =  editor || defaultExprEditor;
    } else if (property.namespace === PropertyNamespace.outputParameter || property.namespace === PropertyNamespace.Out) {
        editor = "#text";
        return {
            ...property,
            dataIndex: property.key,
            name: property.name || property.key,
            editor,
            readonly: true,
        } as FieldEditorConfig;
    }
    return {
        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        editor:editor,
    } as FieldEditorConfig;
}

export function toViewFieldConfig(property: PropertyDescription): FieldEditorConfig {
    if (property.declaredOutputParameter) {
        return {
            ...property,
            dataIndex: property.key,
            name: (property.name?.trim() || property.key),
            readonly: true,
            editor: "bpm-declared-output-catalog",
        } as FieldEditorConfig;
    }
    if (property.customCamundaOutputParameterList) {
        return {
            ...property,
            dataIndex: property.key,
            name: property.name || "自定义输出",
            readonly: true,
            editor: "bpm-camunda-custom-outputs",
        } as FieldEditorConfig;
    }
    return {
        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        readonly: true,
        editor: property.htmlType ?? '#text',
    } as FieldEditorConfig;
}

export function isTextType(property: PropertyDescription): boolean {
    if (property.declaredOutputParameter || property.customCamundaOutputParameterList) {
        return false;
    }
    const textTypes = ['#text', 'textarea', 'input'];

    return property.htmlType ? textTypes.includes(property.htmlType) : true || property.type === 'string';
}

export { PropertyNamespace };
