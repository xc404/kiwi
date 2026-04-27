import { P } from "@antv/g2plot";
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
};


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
    if(property.namespace === PropertyNamespace.inputParameter || property.namespace === PropertyNamespace.In) {
        editor =  editor || defaultExprEditor;
    }else if (property.namespace === PropertyNamespace.outputParameter || property.namespace === PropertyNamespace.Out) {
        editor = '#text';
    }
    return {
        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        editor:editor,
    } as FieldEditorConfig;
}

export function toViewFieldConfig(property: PropertyDescription): FieldEditorConfig {
    return {
        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        readonly: true,
        editor: property.htmlType ?? '#text',
    } as FieldEditorConfig;
}

export function isTextType(property: PropertyDescription): boolean {
    const textTypes = ['#text', 'textarea', 'input'];

    return property.htmlType ? textTypes.includes(property.htmlType) : true || property.type === 'string';
}

export { PropertyNamespace };