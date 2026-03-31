import { FieldEditorConfig } from "@app/shared/components/field/field-editor";
import { Element } from "bpmn-js/lib/model/Types";


enum PropertyNamespace {

    element = "element",
    inputParameter = "inputParameter",
    outputParameter = "outputParameter"
}



export declare type PropertyDescription = {
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

export function toEditFieldConfig(property: PropertyDescription): FieldEditorConfig {
    return {

        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        editor: property.htmlType || 'text',
    }
}

export { PropertyNamespace };