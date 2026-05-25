import { FieldEditorConfig } from "@app/shared/components/field/field-editor";
import { Element } from "bpmn-js/lib/model/Types";
import { ElementModel } from "../extension/element-model";


export enum PropertyNamespace {

    element = "element",
    inputParameter = "inputParameter",
    outputParameter = "outputParameter",
    declaredOutputParameter = "declaredOutputParameter",
}



export declare type PropertyDescription = {
    type?: string;
    key: string;
    name?: string;
    /** 只读行等场景：设计时配置文本（如绑定表达式）；由调用方从模型填充 */
    valueText?: string;
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
     * 静态下拉选项：与 dictKey 二选一，存在时优先于字典。
     * 与 {@link FieldEditorConfig.options} 同形；通过 {@link toEditFieldConfig} 透传给表单。
     */
    options?: Array<{ label: string; value: unknown }>;
};



export type PropertyTab = {
    name?: string;
    groups: { name: string; properties: PropertyDescription[]; important?: boolean }[];
};

export interface PropertyProvider {
    getProperties(element: Element): PropertyTab[];
}



export function toEditFieldConfig(property: PropertyDescription): FieldEditorConfig {

    return {
        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        editor: property.htmlType,
        description: property.description,

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
